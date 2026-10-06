package com.tac5studios.elementseconomy.migrate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.storage.Backups;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementseconomy.storage.TransactionLog;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /economy migrate                    list sources whose data is on this server
 * /economy migrate <source>           preview (target currency, rates, how many balances, total)
 * /economy migrate <source> confirm   import (adds to current balances); "confirm again" to import a source twice
 *
 * Originals are never changed. A backup is taken first. Balances go into the primary currency when it can be
 * changed for offline players, otherwise into digital money.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Migration {

    private static final String META_KEY = "migrations";
    private static final long CONFIRM_MS = 120_000L;
    private static final Map<String, Long> PENDING = new HashMap<>();

    private Migration() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Perm.enabled(Perm.ADMIN_MIGRATE)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("economy").then(Commands.literal("migrate")
                .requires(s -> Perm.has(s, Perm.ADMIN_MIGRATE))
                .executes(Migration::list)
                .then(Commands.argument("source", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(c.getSource().getServer()), b))
                        .executes(c -> preview(c, StringArgumentType.getString(c, "source")))
                        .then(Commands.literal("confirm")
                                .executes(c -> confirm(c, StringArgumentType.getString(c, "source"), false))
                                .then(Commands.literal("again")
                                        .executes(c -> confirm(c, StringArgumentType.getString(c, "source"), true)))))));
    }

    // ---------- sources ----------

    private static List<MigrationSource> sources(MinecraftServer server) {
        List<MigrationSource> out = new ArrayList<>();
        for (MigrationSource s : FileSources.all()) if (s.present(server)) out.add(s);
        out.addAll(CurrencySource.all());
        return out;
    }

    private static List<String> ids(MinecraftServer server) {
        return sources(server).stream().map(MigrationSource::id).toList();
    }

    private static Optional<MigrationSource> source(MinecraftServer server, String id) {
        return sources(server).stream().filter(s -> s.id().equalsIgnoreCase(id)).findFirst();
    }

    /** Primary currency when offline players can receive it, otherwise digital. */
    private static Currency target(Economy e) {
        Currency p = e.primaryCurrency();
        return p.can(Capability.OFFLINE_WRITE) ? p : e.digitalCurrency();
    }

    private static JsonObject done() {
        JsonElement el = Storage.get().raw(Collections.META, META_KEY);
        return el != null && el.isJsonObject() ? el.getAsJsonObject().deepCopy() : new JsonObject();
    }

    private static String who(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        return p == null ? "console" : p.getUUID().toString();
    }

    private static boolean ready(CommandSourceStack src) {
        if (Economy.get() == null || !Economy.running() || !Storage.running()) {
            Msg.fail(src, "general.currency_unavailable");
            return false;
        }
        return true;
    }

    // ---------- commands ----------

    private static int list(CommandContext<CommandSourceStack> c) {
        if (!ready(c.getSource())) return 0;
        List<MigrationSource> all = sources(c.getSource().getServer());
        if (all.isEmpty()) {
            Msg.send(c.getSource(), "migrate.none");
            return 0;
        }
        JsonObject done = done();
        Msg.send(c.getSource(), "migrate.header");
        for (MigrationSource s : all) {
            String key = done.has(s.id()) ? "migrate.line_done" : "migrate.line";
            String when = done.has(s.id()) ? done.getAsJsonObject(s.id()).get("when").getAsString() : "";
            Msg.send(c.getSource(), key, "source", s.name(), "id", s.id(), "when", when);
        }
        return all.size();
    }

    private static int preview(CommandContext<CommandSourceStack> c, String id) {
        CommandSourceStack src = c.getSource();
        if (!ready(src)) return 0;
        Optional<MigrationSource> s = source(src.getServer(), id);
        if (s.isEmpty()) {
            Msg.fail(src, "migrate.unknown", "source", id);
            return 0;
        }
        if (!Features.on(Features.MIGRATION, Features.MIGRATION_PREVIEW)) return run(src, s.get(), false);
        Economy e = Economy.get();
        Currency target = target(e);
        MigrationSource.Result r;
        try {
            r = s.get().read(src.getServer());
        } catch (IOException | RuntimeException ex) {
            ElementsEconomy.LOGGER.error("[Economy] Could not read {} data.", s.get().name(), ex);
            Msg.fail(src, "migrate.read_failed", "source", s.get().name());
            return 0;
        }
        Optional<BigDecimal> tr = e.rate(target);
        if (tr.isEmpty()) {
            Msg.fail(src, "migrate.no_rate", "currency", target.name().getString());
            return 0;
        }
        long players = r.entries().stream().filter(x -> x.player() != null).count();
        long accounts = r.entries().size() - players;
        BigInteger total = BigInteger.ZERO;
        for (MigrationSource.Entry x : r.entries()) total = total.add(units(x.worth(), tr.get()));
        Msg.send(src, "migrate.preview_header", "source", s.get().name(), "currency", target.name().getString());
        Msg.send(src, "migrate.preview_counts", "players", players, "accounts", accounts, "total", e.format(target.of(total)));
        r.rates().forEach((key, rate) -> Msg.send(src, rate.configured() ? "migrate.preview_rate" : "migrate.preview_rate_default",
                "key", key, "rate", rate.value().stripTrailingZeros().toPlainString()));
        if (r.skipped() > 0) Msg.send(src, "migrate.preview_skipped", "count", r.skipped());
        JsonObject done = done();
        if (done.has(s.get().id())) {
            Msg.send(src, "migrate.preview_already", "when", done.getAsJsonObject(s.get().id()).get("when").getAsString(), "id", s.get().id());
        }
        PENDING.put(who(src) + "|" + s.get().id(), System.currentTimeMillis() + CONFIRM_MS);
        Msg.send(src, "migrate.preview_confirm", "id", s.get().id());
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> c, String id, boolean again) {
        CommandSourceStack src = c.getSource();
        if (!ready(src)) return 0;
        Optional<MigrationSource> s = source(src.getServer(), id);
        if (s.isEmpty()) {
            Msg.fail(src, "migrate.unknown", "source", id);
            return 0;
        }
        if (Features.on(Features.MIGRATION, Features.MIGRATION_PREVIEW)) {
            Long until = PENDING.remove(who(src) + "|" + s.get().id());
            if (until == null || until < System.currentTimeMillis()) {
                Msg.fail(src, "migrate.nothing_pending", "id", s.get().id());
                return 0;
            }
        }
        return run(src, s.get(), again);
    }

    // ---------- import ----------

    private static BigInteger units(BigDecimal worth, BigDecimal targetRate) {
        return worth.divide(targetRate, 0, RoundingMode.DOWN).toBigInteger();
    }

    private static int run(CommandSourceStack src, MigrationSource s, boolean again) {
        Economy e = Economy.get();
        JsonObject done = done();
        if (done.has(s.id()) && !again) {
            Msg.fail(src, "migrate.already", "source", s.name(), "id", s.id());
            return 0;
        }
        Currency target = target(e);
        Optional<BigDecimal> tr = e.rate(target);
        if (tr.isEmpty()) {
            Msg.fail(src, "migrate.no_rate", "currency", target.name().getString());
            return 0;
        }
        MigrationSource.Result r;
        try {
            r = s.read(src.getServer());
        } catch (IOException | RuntimeException ex) {
            ElementsEconomy.LOGGER.error("[Economy] Could not read {} data.", s.name(), ex);
            Msg.fail(src, "migrate.read_failed", "source", s.name());
            return 0;
        }
        try {
            Path dir = Backups.take("migrate-" + s.id());
            Msg.send(src, "switch.backup", "folder", dir.getFileName());
        } catch (IOException | RuntimeException ex) {
            ElementsEconomy.LOGGER.error("[Economy] Backup before migration failed.", ex);
            Msg.fail(src, "switch.backup_failed");
            return 0;
        }

        Cause cause = Cause.system("migration from " + s.name());
        int imported = 0, failed = 0;
        BigInteger total = BigInteger.ZERO;
        for (MigrationSource.Entry x : r.entries()) {
            BigInteger units = units(x.worth(), tr.get());
            if (units.signum() <= 0) continue;
            boolean ok;
            if (x.player() != null) {
                ok = e.deposit(x.player(), target.of(units), cause).success();
            } else {
                ok = addAccount("octo:" + x.account(), target, units);
            }
            if (ok) {
                imported++;
                total = total.add(units);
            } else {
                failed++;
            }
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("when", Instant.now().toString().substring(0, 10));
        entry.addProperty("count", imported);
        entry.addProperty("currency", target.id().toString());
        done.add(s.id(), entry);
        Storage.get().put(Collections.META, META_KEY, done);
        Storage.moneyChanged();

        String line = s.name() + ": " + imported + " balances, " + e.format(target.of(total)) + " (" + target.id() + ")"
                + (failed > 0 ? ", " + failed + " failed" : "");
        TransactionLog.add("MIGRATE", line);
        ElementsEconomy.LOGGER.info("[Economy] Migration from {}", line);
        Msg.sendLogged(src, "migrate.done", "count", imported, "source", s.name(), "total", e.format(target.of(total)));
        if (failed > 0) Msg.send(src, "migrate.failed", "count", failed);
        return imported;
    }

    /** Add to a named bridge account (OctoEconomy bank accounts and the like). */
    private static boolean addAccount(String key, Currency target, BigInteger units) {
        JsonObject o = Storage.get().get(Collections.BRIDGE_ACCOUNTS, key, JsonObject.class);
        BigInteger now = BigInteger.ZERO;
        if (o != null && o.has("amount")) {
            if (o.has("currency") && !o.get("currency").getAsString().equals(target.id().toString())) return false;
            now = new BigInteger(o.get("amount").getAsString());
        }
        JsonObject out = new JsonObject();
        out.addProperty("currency", target.id().toString());
        out.addProperty("amount", now.add(units).toString());
        Storage.get().put(Collections.BRIDGE_ACCOUNTS, key, out);
        return true;
    }
}
