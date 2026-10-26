package com.tac5studios.elementseconomy.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Coins;
import com.tac5studios.elementseconomy.core.DigitalCurrency;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.core.ItemCurrency;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /exchange deposit [amount] [item] and /exchange withdraw <amount> [item]: swap the currency items a player carries for
 * digital money (the bank) and back.
 *
 * <p>Works while an item currency is the primary currency (digital money is always kept next to it), and while digital
 * money is primary with an item currency installed. Always 1:1: one smallest currency item = one digital money.
 *
 * <p>The optional item multiplies the amount by that item's value from currency_values.toml. Without it the amount is in
 * the smallest item. Withdrawals pay out in the fewest items.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ExchangeCommands {

    private ExchangeCommands() {}

    private static final SuggestionProvider<CommandSourceStack> COINS = (c, b) -> {
        Economy e = Economy.get();
        ItemCurrency coin = e == null ? null : e.bankCoins();
        List<String> names = new ArrayList<>();
        if (coin != null) {
            for (Map.Entry<Item, Long> d : CurrencyDetector.denominations(coin.namespace())) {
                if (d.getValue() > 0) names.add(exchangeName(d.getKey()));
            }
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        if (!Features.on(Features.ECONOMY)) return;
        register(e.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        boolean deposit = Perm.enabled(Perm.EXCHANGE_DEPOSIT), withdraw = Perm.enabled(Perm.EXCHANGE_WITHDRAW);
        if (!deposit && !withdraw) return;
        LiteralArgumentBuilder<CommandSourceStack> ex = Commands.literal("exchange")
                .requires(s -> Perm.has(s, Perm.EXCHANGE_DEPOSIT) || Perm.has(s, Perm.EXCHANGE_WITHDRAW));
        if (deposit) {
            ex.then(Commands.literal("deposit")
                    .requires(s -> Perm.has(s, Perm.EXCHANGE_DEPOSIT))
                    .executes(c -> deposit(c, null, null))
                    .then(Commands.argument("amount", StringArgumentType.word())
                            .executes(c -> deposit(c, StringArgumentType.getString(c, "amount"), null))
                            .then(Commands.argument("item", StringArgumentType.greedyString()).suggests(COINS)
                                    .executes(c -> deposit(c, StringArgumentType.getString(c, "amount"),
                                            StringArgumentType.getString(c, "item"))))));
        }
        if (withdraw) {
            ex.then(Commands.literal("withdraw")
                    .requires(s -> Perm.has(s, Perm.EXCHANGE_WITHDRAW))
                    .then(Commands.argument("amount", StringArgumentType.word())
                            .executes(c -> withdraw(c, StringArgumentType.getString(c, "amount"), null))
                            .then(Commands.argument("item", StringArgumentType.greedyString()).suggests(COINS)
                                    .executes(c -> withdraw(c, StringArgumentType.getString(c, "amount"),
                                            StringArgumentType.getString(c, "item"))))));
        }
        d.register(ex);
    }

    // ---------- /exchange deposit ----------

    private static int deposit(CommandContext<CommandSourceStack> c, String input, String coinName) {
        CommandSourceStack s = c.getSource();
        Setup x = setup(s);
        if (x == null) return 0;

        BigInteger have = x.e.balance(x.p.getUUID(), x.coin).amount();
        if (have.signum() <= 0) {
            Msg.fail(s, "exchange.no_coins");
            return 0;
        }
        BigInteger coins = have;
        if (input != null) {
            BigInteger asked = coinAmount(s, x, input, coinName);
            if (asked == null) return 0;
            coins = asked;
            if (coins.compareTo(have) > 0) {
                Msg.fail(s, "exchange.not_enough_coins", "coins", fmt(x.coin, have));
                return 0;
            }
        }

        BigInteger out = x.toDigital(coins);
        if (out.signum() <= 0) {
            Msg.fail(s, "exchange.too_small");
            return 0;
        }
        // Only take the coins the digital amount is really worth, so nothing is lost to rounding.
        coins = x.coin.roundUp(x.coinsWorth(out)).min(coins);

        Cause cause = Cause.player(x.p.getUUID(), "exchange deposit");
        Result taken = x.e.withdraw(x.p.getUUID(), x.coin.of(coins), cause);
        if (!taken.success()) {
            Msg.fail(s, taken.reason() == Result.Reason.INSUFFICIENT_FUNDS ? "exchange.no_coins" : "general.error");
            return 0;
        }
        Result given = x.e.deposit(x.p.getUUID(), x.digital.of(out), cause);
        if (!given.success()) {
            x.e.deposit(x.p.getUUID(), x.coin.of(coins), Cause.system("refund: exchange deposit"));
            Msg.fail(s, "general.error");
            return 0;
        }
        Msg.send(x.p, "exchange.deposited", "coins", fmt(x.coin, coins), "amount", fmt(x.digital, out));
        return 1;
    }

    // ---------- /exchange withdraw ----------

    private static int withdraw(CommandContext<CommandSourceStack> c, String input, String coinName) {
        CommandSourceStack s = c.getSource();
        Setup x = setup(s);
        if (x == null) return 0;

        BigInteger asked;
        if (coinName != null) {
            BigInteger coinValue = coinAmount(s, x, input, coinName);
            if (coinValue == null) return 0;
            asked = x.digitalCost(coinValue);
        } else {
            Money m = x.e.parse(x.digital, input).orElse(null);
            if (m == null || m.amount().signum() <= 0) {
                Msg.fail(s, "general.bad_amount", "input", input);
                return 0;
            }
            asked = m.amount();
        }
        Money have = x.e.balance(x.p.getUUID(), x.digital);
        if (have.amount().compareTo(asked) < 0) {
            Msg.fail(s, "pay.not_enough", "balance", fmt(x.digital, have.amount()));
            return 0;
        }

        BigInteger coins = x.coin.payable(x.toCoins(asked));
        if (coins.bitLength() > 62) {
            Msg.fail(s, "general.bad_amount", "input", input);
            return 0;
        }
        if (coins.signum() <= 0) {
            Msg.fail(s, "exchange.too_small");
            return 0;
        }
        BigInteger cost = x.digitalCost(coins).min(asked);

        int free = 0;
        for (ItemStack st : x.p.getInventory().items) if (st.isEmpty()) free++;
        boolean full = Coins.stacks(x.coin.namespace(), coins.longValueExact()).size() > free;

        Cause cause = Cause.player(x.p.getUUID(), "exchange withdraw");
        Result taken = x.e.withdraw(x.p.getUUID(), x.digital.of(cost), cause);
        if (!taken.success()) {
            Msg.fail(s, "general.error");
            return 0;
        }
        Result given = x.e.deposit(x.p.getUUID(), x.coin.of(coins), cause);
        if (!given.success()) {
            x.e.deposit(x.p.getUUID(), x.digital.of(cost), Cause.system("refund: exchange withdraw"));
            Msg.fail(s, "general.error");
            return 0;
        }
        Msg.send(x.p, "exchange.withdrew", "amount", fmt(x.digital, cost), "coins", fmt(x.coin, coins));
        BigInteger left = asked.subtract(cost);
        if (left.signum() > 0) Msg.send(x.p, "exchange.remainder", "amount", fmt(x.digital, left));
        if (full) Msg.send(x.p, "exchange.no_room");
        return 1;
    }

    // ---------- helpers ----------

    /**
     * Typed amount in the smallest coin, or "count" x the named coin's value. Null (and a message sent) when either part
     * is wrong.
     */
    private static BigInteger coinAmount(CommandSourceStack s, Setup x, String input, String coinName) {
        if (coinName == null) {
            Money m = x.e.parse(x.coin, input).orElse(null);
            if (m == null || m.amount().signum() <= 0) {
                Msg.fail(s, "general.bad_amount", "input", input);
                return null;
            }
            return m.amount();
        }
        Long value = coinValue(x.coin, coinName);
        if (value == null) {
            Msg.fail(s, "exchange.unknown_coin", "coin", coinName);
            return null;
        }
        BigInteger count;
        try {
            count = new BigInteger(input.replace(",", ""));
        } catch (NumberFormatException ex) {
            count = null;
        }
        if (count == null || count.signum() <= 0 || count.bitLength() > 40) {
            Msg.fail(s, "general.bad_amount", "input", input);
            return null;
        }
        return count.multiply(BigInteger.valueOf(value));
    }

    /**
     * Value of the currency item called {@code typed}, or null. Matches the names set in economy.toml [exchange] names,
     * the automatic name (the item's in-game name without "Coin"), the item path and the full item ID. Not case-sensitive.
     */
    private static Long coinValue(ItemCurrency coin, String typed) {
        String t = typed.trim().toLowerCase(Locale.ROOT);
        ResourceLocation asId = t.contains(":") ? ResourceLocation.tryParse(t) : null;
        for (Map.Entry<Item, Long> d : CurrencyDetector.denominations(coin.namespace())) {
            if (d.getValue() <= 0) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(d.getKey());
            if (id.equals(asId)) return d.getValue();
            String auto = autoName(d.getKey()).toLowerCase(Locale.ROOT);
            if (t.equals(exchangeName(d.getKey()).toLowerCase(Locale.ROOT)) || t.equals(auto) || t.equals(auto + "_coin")
                    || t.equals(id.getPath())) return d.getValue();
        }
        return null;
    }

    /** Name shown in the Tab list and accepted when typed: economy.toml [exchange] names, else the automatic name. */
    private static String exchangeName(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        for (String line : EconomyConfig.EXCHANGE_NAMES.get()) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = line.substring(eq + 1).trim().replace(' ', '_');
            if (line.substring(0, eq).trim().equals(id) && !name.isEmpty()) return name;
        }
        return autoName(item);
    }

    /** "Bronze Coin" -> "Bronze". Falls back to the item id path. */
    private static String autoName(Item item) {
        String s = item.getDescription().getString().replaceAll("§.", "").trim();
        String low = s.toLowerCase(Locale.ROOT);
        if (low.endsWith(" coins")) s = s.substring(0, s.length() - 6);
        else if (low.endsWith(" coin")) s = s.substring(0, s.length() - 5);
        s = s.trim().replace(' ', '_');
        return s.isEmpty() || !s.matches("[A-Za-z0-9_]+") ? BuiltInRegistries.ITEM.getKey(item).getPath() : s;
    }

    private static String fmt(Currency c, BigInteger amount) {
        return c.format(amount).getString();
    }

    private static Setup setup(CommandSourceStack s) {
        Economy e = Economy.get();
        if (e == null || !Economy.running()) {
            Msg.fail(s, "general.currency_unavailable");
            return null;
        }
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            Msg.fail(s, "general.players_only");
            return null;
        }
        ItemCurrency coin = e.bankCoins();
        if (coin == null) {
            Msg.fail(s, "exchange.no_currency");
            return null;
        }
        DigitalCurrency digital = e.digitalCurrency();
        BigDecimal unit = BigDecimal.ONE.movePointLeft(digital.decimals());
        return new Setup(e, p, coin, digital, e.coinRate(coin), unit);
    }

    /** Everything one exchange needs. Rates are "worth of one smallest unit, in whole digital money". */
    private record Setup(Economy e, ServerPlayer p, ItemCurrency coin, DigitalCurrency digital, BigDecimal coinRate, BigDecimal unit) {

        BigInteger toDigital(BigInteger coins) {
            return new BigDecimal(coins).multiply(coinRate).divide(unit, 0, RoundingMode.DOWN).toBigInteger();
        }

        BigInteger toCoins(BigInteger digitalAmount) {
            return new BigDecimal(digitalAmount).multiply(unit).divide(coinRate, 0, RoundingMode.DOWN).toBigInteger();
        }

        /** Coins needed for this much digital money, rounded up. */
        BigInteger coinsWorth(BigInteger digitalAmount) {
            return new BigDecimal(digitalAmount).multiply(unit).divide(coinRate, 0, RoundingMode.UP).toBigInteger();
        }

        /** Digital money these coins cost, rounded up so the server never pays out more than it takes. */
        BigInteger digitalCost(BigInteger coins) {
            return new BigDecimal(coins).multiply(coinRate).divide(unit, 0, RoundingMode.UP).toBigInteger();
        }
    }
}
