package com.tac5studios.elementseconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.StorageConfig;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.shop.ShopDisplays;
import com.tac5studios.elementseconomy.shop.Shop;
import com.tac5studios.elementseconomy.shop.Shops;
import com.tac5studios.elementseconomy.storage.Backups;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementseconomy.storage.StorageConvert;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * /economy reload                  messages.toml, currency_values.toml and the currency list (config .toml files
 *                                  reload on their own when saved; "(restart)" settings still need a restart)
 * /economy version                 mod version, primary currency, storage type
 * /economy storage convert <type>  copy all data to json / yaml / sqlite / mysql (backup first; the running
 *                                  storage is not changed — set the new type in storage.toml and restart)
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class AdminCommands {

    private static final List<String> TYPES = List.of("json", "yaml", "sqlite", "mysql");

    private AdminCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> eco = Commands.literal("economy");
        boolean any = false;
        if (Perm.enabled(Perm.ADMIN_RELOAD)) {
            eco.then(Commands.literal("reload").requires(s -> Perm.has(s, Perm.ADMIN_RELOAD)).executes(AdminCommands::reload));
            any = true;
        }
        if (Perm.enabled(Perm.ADMIN_VERSION)) {
            eco.then(Commands.literal("version").requires(s -> Perm.has(s, Perm.ADMIN_VERSION)).executes(AdminCommands::version));
            any = true;
        }
        if (Perm.enabled(Perm.ADMIN_STORAGE)) {
            eco.then(Commands.literal("storage").requires(s -> Perm.has(s, Perm.ADMIN_STORAGE))
                    .then(Commands.literal("convert")
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                            TYPES.stream().filter(t -> !t.equals(current())).toList(), b))
                                    .executes(c -> convert(c, StringArgumentType.getString(c, "type"))))));
            any = true;
        }
        if (any) d.register(eco);
    }

    private static String current() {
        return StorageConfig.BACKEND.get();
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        Msg.load();
        CurrencyDetector.detect();
        Economy e = Economy.get();
        if (e != null) e.reload();
        for (Shop s : Shops.all()) ShopDisplays.touch(s.id); // signs pick up new messages and prices
        MinecraftServer server = c.getSource().getServer();
        if (!CurrencyDetector.pendingVanilla().isEmpty()) CurrencyDetector.askAllAdmins(server);
        Msg.sendLogged(c.getSource(), "admin.reloaded");
        ElementsEconomy.LOGGER.info("[Economy] Reloaded messages and currency values.");
        return 1;
    }

    private static int version(CommandContext<CommandSourceStack> c) {
        String version = ModList.get().getModContainerById(ElementsEconomy.MOD_ID)
                .map(m -> m.getModInfo().getVersion().toString()).orElse("?");
        Economy e = Economy.get();
        String currency = e == null || e.primaryCurrency() == null ? "-" : e.primaryCurrency().name().getString();
        String storage = Storage.running() ? current() : "-";
        Msg.send(c.getSource(), "admin.version", "version", version, "currency", currency, "storage", storage);
        return 1;
    }

    private static int convert(CommandContext<CommandSourceStack> c, String typed) {
        CommandSourceStack src = c.getSource();
        String type = typed.toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            Msg.fail(src, "admin.convert_unknown", "type", typed, "types", String.join(", ", TYPES));
            return 0;
        }
        if (!Storage.running()) {
            Msg.fail(src, "general.error");
            return 0;
        }
        if (type.equals(current())) {
            Msg.fail(src, "admin.convert_same", "type", type);
            return 0;
        }
        if (StorageConvert.running()) {
            Msg.fail(src, "admin.convert_running");
            return 0;
        }
        try {
            Path dir = Backups.take("storage-convert");
            Msg.send(src, "switch.backup", "folder", dir.getFileName());
        } catch (IOException | RuntimeException ex) {
            ElementsEconomy.LOGGER.error("[Economy] Backup before storage convert failed.", ex);
            Msg.fail(src, "switch.backup_failed");
            return 0;
        }
        Msg.sendLogged(src, "admin.convert_started", "type", type);
        MinecraftServer server = src.getServer();
        StorageConvert.start(type, error -> server.execute(() -> {
            if (error == null) Msg.sendLogged(src, "admin.convert_done", "type", type);
            else Msg.fail(src, "admin.convert_failed", "type", type, "error", error);
        }));
        return 1;
    }
}
