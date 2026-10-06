package com.tac5studios.elementseconomy.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Accounts;
import com.tac5studios.elementseconomy.core.Amounts;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * /bal, /pay, /pay toggle, /baltop, /payments, /eco give|take|set|reset.
 * A command whose switch is off is not registered at all.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class MoneyCommands {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private MoneyCommands() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.ECONOMY)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        // /bal [player]  (+ /balance)
        if (Perm.enabled(Perm.BALANCE)) {
            for (String name : new String[]{"bal", "balance"}) {
                LiteralArgumentBuilder<CommandSourceStack> bal = Commands.literal(name)
                        .requires(s -> Perm.has(s, Perm.BALANCE))
                        .executes(MoneyCommands::balSelf);
                if (Perm.enabled(Perm.BALANCE_OTHERS)) {
                    bal.then(Commands.argument("player", GameProfileArgument.gameProfile())
                            .requires(s -> Perm.has(s, Perm.BALANCE_OTHERS))
                            .executes(MoneyCommands::balOther));
                }
                d.register(bal);
            }
        }

        // /pay <player> <amount> [note]  and  /pay toggle
        if (Perm.enabled(Perm.PAY)) {
            LiteralArgumentBuilder<CommandSourceStack> pay = Commands.literal("pay")
                    .requires(s -> Perm.has(s, Perm.PAY));
            if (Perm.enabled(Perm.PAY_TOGGLE)) {
                pay.then(Commands.literal("toggle")
                        .requires(s -> Perm.has(s, Perm.PAY_TOGGLE))
                        .executes(MoneyCommands::payToggle));
            }
            pay.then(Commands.argument("player", GameProfileArgument.gameProfile())
                    .then(Commands.argument("amount", StringArgumentType.word())
                            .executes(c -> pay(c, ""))
                            .then(Commands.argument("note", StringArgumentType.greedyString())
                                    .executes(c -> pay(c, StringArgumentType.getString(c, "note"))))));
            d.register(pay);
        }

        // /baltop [page]
        if (Perm.enabled(Perm.BALTOP)) {
            d.register(Commands.literal("baltop")
                    .requires(s -> Perm.has(s, Perm.BALTOP))
                    .executes(c -> baltop(c, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(c -> baltop(c, IntegerArgumentType.getInteger(c, "page")))));
        }

        // /payments [page]  and  /payments <player> [page]
        if (Perm.enabled(Perm.HISTORY)) {
            LiteralArgumentBuilder<CommandSourceStack> hist = Commands.literal("payments")
                    .requires(s -> Perm.has(s, Perm.HISTORY))
                    .executes(c -> historySelf(c, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(c -> historySelf(c, IntegerArgumentType.getInteger(c, "page"))));
            if (Perm.enabled(Perm.HISTORY_OTHERS)) {
                hist.then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(s -> Perm.has(s, Perm.HISTORY_OTHERS))
                        .executes(c -> historyOther(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> historyOther(c, IntegerArgumentType.getInteger(c, "page")))));
            }
            d.register(hist);
        }

        // /eco give|take|set|reset
        boolean give = Perm.enabled(Perm.ECO_GIVE), take = Perm.enabled(Perm.ECO_TAKE);
        boolean set = Perm.enabled(Perm.ECO_SET), reset = Perm.enabled(Perm.ECO_RESET);
        if (give || take || set || reset) {
            LiteralArgumentBuilder<CommandSourceStack> eco = Commands.literal("eco")
                    .requires(s -> Perm.has(s, Perm.ECO_GIVE) || Perm.has(s, Perm.ECO_TAKE)
                            || Perm.has(s, Perm.ECO_SET) || Perm.has(s, Perm.ECO_RESET));
            if (give) eco.then(ecoAmount("give", Perm.ECO_GIVE, Op.GIVE));
            if (take) eco.then(ecoAmount("take", Perm.ECO_TAKE, Op.TAKE));
            if (set) eco.then(ecoAmount("set", Perm.ECO_SET, Op.SET));
            if (reset) {
                eco.then(Commands.literal("reset")
                        .requires(s -> Perm.has(s, Perm.ECO_RESET))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(MoneyCommands::ecoReset)));
            }
            d.register(eco);
        }
    }

    // ---------- helpers ----------

    private static Economy eco(CommandSourceStack s) {
        Economy e = Economy.get();
        if (e == null || !Economy.running()) {
            Msg.fail(s, "general.currency_unavailable");
            return null;
        }
        return e;
    }

    /** Exactly one player from a name or selector (unknown names already fail in the argument). */
    private static GameProfile one(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<GameProfile> list = GameProfileArgument.getGameProfiles(c, "player");
        if (list.size() != 1) {
            Msg.fail(c.getSource(), "general.player_not_found", "player", list.size() + " players");
            return null;
        }
        return list.iterator().next();
    }

    private static String fmt(Economy e, Money m) {
        return e.format(m);
    }

    // ---------- /bal ----------

    private static int balSelf(CommandContext<CommandSourceStack> c) {
        Economy e = eco(c.getSource());
        if (e == null) return 0;
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            Msg.fail(c.getSource(), "general.players_only");
            return 0;
        }
        for (Currency cur : e.activeCurrencies()) {
            Msg.send(c.getSource(), "balance.self", "balance", fmt(e, e.balance(p.getUUID(), cur)));
        }
        return 1;
    }

    private static int balOther(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Economy e = eco(c.getSource());
        if (e == null) return 0;
        GameProfile gp = one(c);
        if (gp == null) return 0;
        for (Currency cur : e.activeCurrencies()) {
            if (!Economy.isOnline(gp.getId()) && !cur.can(Capability.OFFLINE_READ)) {
                Msg.fail(c.getSource(), "general.player_offline", "player", gp.getName());
                continue;
            }
            Msg.send(c.getSource(), "balance.other", "player", gp.getName(), "balance", fmt(e, e.balance(gp.getId(), cur)));
        }
        return 1;
    }

    // ---------- /pay ----------

    private static int pay(CommandContext<CommandSourceStack> c, String note) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        Economy e = eco(s);
        if (e == null) return 0;
        ServerPlayer payer = s.getPlayer();
        if (payer == null) {
            Msg.fail(s, "general.players_only");
            return 0;
        }
        GameProfile target = one(c);
        if (target == null) return 0;
        UUID to = target.getId();
        if (to.equals(payer.getUUID())) {
            Msg.fail(s, "pay.self");
            return 0;
        }
        if (Features.on(Features.ECO_PAY_TOGGLE) && !Accounts.acceptsPayments(to)) {
            Msg.fail(s, "pay.blocked", "player", target.getName());
            return 0;
        }

        Currency cur = e.primaryCurrency();
        boolean online = Economy.isOnline(to);
        if (!online) {
            if (!Features.on(Features.ECO_PAY_OFFLINE) || !Perm.has(s, Perm.PAY_OFFLINE)) {
                Msg.fail(s, "general.player_offline", "player", target.getName());
                return 0;
            }
            if (!cur.can(Capability.OFFLINE_WRITE)) {
                Msg.fail(s, "pay.offline_not_supported", "player", target.getName());
                return 0;
            }
        }

        String input = StringArgumentType.getString(c, "amount");
        Money amount = e.parse(cur, input).orElse(null);
        BigInteger min = Economy.configAmount(cur, EconomyConfig.PAY_MIN.get()).orElse(BigInteger.ONE);
        if (amount == null || amount.amount().compareTo(min) < 0) {
            Msg.fail(s, "general.bad_amount", "input", input);
            return 0;
        }
        Money have = e.balance(payer.getUUID(), cur);
        if (have.compareTo(amount) < 0) {
            Msg.fail(s, "pay.not_enough", "balance", fmt(e, have));
            return 0;
        }

        Result r = e.transfer(payer.getUUID(), to, amount, Cause.player(payer.getUUID(), note));
        if (!r.success()) {
            failure(s, r, target.getName(), e, have);
            return 0;
        }
        Msg.send(payer, "pay.sent", "player", target.getName(), "amount", fmt(e, amount));
        ServerPlayer receiver = s.getServer().getPlayerList().getPlayer(to);
        if (receiver != null) {
            Msg.send(receiver, "pay.received", "player", payer.getGameProfile().getName(), "amount", fmt(e, amount));
        }
        return 1;
    }

    private static void failure(CommandSourceStack s, Result r, String player, Economy e, Money have) {
        switch (r.reason()) {
            case INSUFFICIENT_FUNDS -> Msg.fail(s, "pay.not_enough", "balance", fmt(e, have));
            case OFFLINE_NOT_SUPPORTED -> Msg.fail(s, "pay.offline_not_supported", "player", player);
            case INVALID_AMOUNT -> Msg.fail(s, "general.bad_amount", "input", fmt(e, r.amount()));
            default -> Msg.fail(s, "general.error");
        }
    }

    private static int payToggle(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null || eco(c.getSource()) == null) return 0;
        boolean on = Accounts.togglePayments(p.getUUID());
        Msg.send(p, on ? "pay.toggle_on" : "pay.toggle_off");
        return 1;
    }

    // ---------- /baltop ----------

    private static int baltop(CommandContext<CommandSourceStack> c, int page) {
        CommandSourceStack s = c.getSource();
        Economy e = eco(s);
        if (e == null) return 0;
        Currency cur = e.primaryCurrency();
        if (!cur.can(Capability.LIST_ALL)) {
            Msg.fail(s, "baltop.not_supported");
            return 0;
        }
        int size = EconomyConfig.BALTOP_PAGE.get();
        List<Holding> all = e.top(cur, 1000);
        if (all.isEmpty()) {
            Msg.send(s, "baltop.empty");
            return 0;
        }
        int pages = (all.size() + size - 1) / size;
        page = Math.min(page, pages);
        Msg.send(s, "baltop.header", "page", page, "pages", pages);
        for (int i = (page - 1) * size; i < Math.min(all.size(), page * size); i++) {
            Holding h = all.get(i);
            Msg.send(s, "baltop.line", "rank", i + 1, "player", Accounts.name(h.player()), "balance", fmt(e, h.money()));
        }
        return 1;
    }

    // ---------- /payments ----------

    private static int historySelf(CommandContext<CommandSourceStack> c, int page) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) {
            Msg.fail(c.getSource(), "general.players_only");
            return 0;
        }
        return history(c.getSource(), p.getUUID(), p.getGameProfile().getName(), page);
    }

    private static int historyOther(CommandContext<CommandSourceStack> c, int page) throws CommandSyntaxException {
        GameProfile gp = one(c);
        return gp == null ? 0 : history(c.getSource(), gp.getId(), gp.getName(), page);
    }

    private static int history(CommandSourceStack s, UUID id, String name, int page) {
        Economy e = eco(s);
        if (e == null) return 0;
        List<Accounts.Entry> list = Accounts.history(id);
        if (list.isEmpty()) {
            Msg.send(s, "history.empty");
            return 0;
        }
        int size = EconomyConfig.HISTORY_PAGE.get();
        int pages = (list.size() + size - 1) / size;
        page = Math.min(page, pages);
        Msg.send(s, "history.header", "player", name, "page", page, "pages", pages);
        for (int i = (page - 1) * size; i < Math.min(list.size(), page * size); i++) {
            Accounts.Entry en = list.get(i);
            String amount = en.amount();
            ResourceLocation cid = ResourceLocation.tryParse(en.currency());
            if (cid != null) {
                var cur = e.currency(cid);
                if (cur.isPresent()) {
                    try {
                        amount = fmt(e, cur.get().of(new BigInteger(en.amount())));
                    } catch (NumberFormatException ignored) {
                        // keep the raw number
                    }
                }
            }
            String other = en.other() == null ? en.kind() : Accounts.name(en.other());
            Msg.send(s, en.incoming() ? "history.line_in" : "history.line_out",
                    "time", TIME.format(en.time()), "amount", amount, "other", other, "note", en.note());
        }
        return 1;
    }

    // ---------- /eco ----------

    private enum Op { GIVE, TAKE, SET }

    private static LiteralArgumentBuilder<CommandSourceStack> ecoAmount(String name, String node, Op op) {
        return Commands.literal(name)
                .requires(s -> Perm.has(s, node))
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(c -> eco(c, op))));
    }

    private static int eco(CommandContext<CommandSourceStack> c, Op op) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        Economy e = eco(s);
        if (e == null) return 0;
        GameProfile gp = one(c);
        if (gp == null) return 0;
        Currency cur = e.primaryCurrency();
        String input = StringArgumentType.getString(c, "amount");
        BigInteger value = (op == Op.SET ? Amounts.parseOrZero(input, cur.decimals()) : cur.parse(input)).orElse(null);
        if (value == null) {
            Msg.fail(s, "general.bad_amount", "input", input);
            return 0;
        }
        Money m = cur.of(value);
        UUID actor = s.getPlayer() == null ? null : s.getPlayer().getUUID();
        Cause cause = Cause.command("/eco " + op.name().toLowerCase(), actor);
        Result r = switch (op) {
            case GIVE -> e.deposit(gp.getId(), m, cause);
            case TAKE -> e.withdraw(gp.getId(), m, cause);
            case SET -> e.set(gp.getId(), m, cause);
        };
        if (!r.success()) {
            ecoFailure(s, r, gp.getName());
            return 0;
        }
        String bal = fmt(e, e.balance(gp.getId(), cur));
        switch (op) {
            case GIVE -> Msg.sendLogged(s, "eco.give", "player", gp.getName(), "amount", fmt(e, m), "balance", bal);
            case TAKE -> Msg.sendLogged(s, "eco.take", "player", gp.getName(), "amount", fmt(e, m), "balance", bal);
            case SET -> Msg.sendLogged(s, "eco.set", "player", gp.getName(), "balance", bal);
        }
        return 1;
    }

    private static int ecoReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        Economy e = eco(s);
        if (e == null) return 0;
        GameProfile gp = one(c);
        if (gp == null) return 0;
        Currency cur = e.primaryCurrency();
        BigInteger start = cur.equals(e.digitalCurrency()) && Features.on(Features.ECO_STARTING_BALANCE)
                ? Amounts.parseOrZero(EconomyConfig.STARTING_BALANCE.get(), cur.decimals()).orElse(BigInteger.ZERO)
                : BigInteger.ZERO;
        UUID actor = s.getPlayer() == null ? null : s.getPlayer().getUUID();
        Result r = e.set(gp.getId(), cur.of(start), Cause.command("/eco reset", actor));
        if (!r.success()) {
            ecoFailure(s, r, gp.getName());
            return 0;
        }
        Msg.sendLogged(s, "eco.reset", "player", gp.getName(), "balance", fmt(e, cur.of(start)));
        return 1;
    }

    private static void ecoFailure(CommandSourceStack s, Result r, String player) {
        switch (r.reason()) {
            case OFFLINE_NOT_SUPPORTED -> Msg.fail(s, "general.player_offline", "player", player);
            case INSUFFICIENT_FUNDS, NOT_SUPPORTED -> Msg.fail(s, "eco.not_supported");
            default -> Msg.fail(s, "general.error");
        }
    }
}
