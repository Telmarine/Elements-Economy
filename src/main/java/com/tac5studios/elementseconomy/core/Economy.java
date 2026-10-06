package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.KnownCurrency;
import com.tac5studios.elementseconomy.storage.TransactionLog;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.EconomyAPI;
import com.tac5studios.elementsvault.EconomyService;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.backend.CurrencyBackend;
import com.tac5studios.elementsvault.event.BalanceChangedEvent;
import com.tac5studios.elementsvault.event.RegisterCurrencyBackendsEvent;
import com.tac5studios.elementsvault.event.TransactionEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The economy core: every currency backend, the primary currency, and every money change.
 * Commands, shops and the auction house call this directly. Other mods reach it through
 * Elements: Vault ({@link EconomyAPI}), wrapped in {@link ApiFacade} so the api.* switches apply.
 *
 * Starts after storage and currency detection, stops before storage closes.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Economy implements EconomyService {

    private static Economy instance;

    private final Map<ResourceLocation, CurrencyBackend> backends = new LinkedHashMap<>();
    private final DigitalCurrency digitalCurrency = new DigitalCurrency();
    private final DigitalBackend digital = new DigitalBackend(digitalCurrency);
    private Currency primary;
    private ApiFacade facade;

    private Economy() {}

    /** The running economy, or null when the economy is off or the server isn't running. */
    public static Economy get() {
        return instance;
    }

    public static boolean running() {
        return instance != null && instance.primary != null;
    }

    // ---------- server events ----------

    @SubscribeEvent(priority = EventPriority.LOW) // after storage (HIGHEST) and detection (NORMAL)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        if (!Features.on(Features.ECONOMY)) {
            ElementsEconomy.LOGGER.info("[Economy] Economy is turned off in features.toml.");
            return;
        }
        instance = new Economy();
        instance.start();
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        if (instance == null) return;
        if (instance.facade != null) EconomyAPI.clear(instance.facade);
        instance = null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (instance == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        Accounts.seen(p);
        instance.startingBalance(p);
    }

    // ---------- setup ----------

    private void start() {
        if (facade != null) {
            EconomyAPI.clear(facade);
            facade = null;
        }
        backends.clear();
        backends.put(digitalCurrency.id(), digital);

        // Coins from detected mods and confirmed vanilla items.
        for (String ns : CurrencyDetector.itemCurrencies()) {
            ItemCurrency c = new ItemCurrency(ns, displayName(ns));
            ItemBackend b = new ItemBackend(c);
            if (b.isAvailable()) backends.put(c.id(), b);
        }

        // Currency mods that keep money in their own data (API, player data, files).
        for (CurrencyBackend b : com.tac5studios.elementseconomy.adapters.Adapters.forDetected()) {
            backends.putIfAbsent(b.currency().id(), b);
        }

        // Other mods' money systems.
        if (Features.on(Features.API, Features.API_EXTERNAL_BACKENDS)) {
            NeoForge.EVENT_BUS.post(new RegisterCurrencyBackendsEvent(b -> {
                if (b.isAvailable() && !backends.containsKey(b.currency().id())) {
                    backends.put(b.currency().id(), b);
                    ElementsEconomy.LOGGER.info("[Economy] Currency added by another mod: {}", b.currency().id());
                }
            }));
        }

        primary = pickPrimary();
        if (primary == null) {
            ElementsEconomy.LOGGER.warn("[Economy] No currency to use. Turn on currency.digital or install a currency mod.");
            return;
        }
        ElementsEconomy.LOGGER.info("[Economy] Primary currency: {} ({})", primary.name().getString(), primary.id());

        if (Features.on(Features.API)) {
            facade = new ApiFacade(this);
            EconomyAPI.provide(facade);
        }
    }

    /** Rebuild after currency_values.toml or a vanilla confirm changed the coins. */
    public void reload() {
        start();
    }

    private static String displayName(String ns) {
        if ("minecraft".equals(ns)) return "Confirmed vanilla items";
        for (KnownCurrency k : CurrencyDetector.found()) {
            if (k.modId().equals(ns)) return k.name();
        }
        return ns;
    }

    private Currency pickPrimary() {
        String wanted = EconomyConfig.PRIMARY.get().trim();
        if (!wanted.isEmpty() && !"auto".equalsIgnoreCase(wanted)) {
            ResourceLocation id = ResourceLocation.tryParse(wanted);
            if (id != null && backends.containsKey(id)) return backends.get(id).currency();
            ElementsEconomy.LOGGER.warn("[Economy] primary_currency '{}' is not installed. Using auto.", wanted);
        }
        Currency detected = firstDetected();
        if (Features.on(Features.CURRENCY_USE_DETECTED) && detected == null) {
            // Found a currency mod, but nothing here can read its balances yet: stay on digital so /bal is never wrong.
            for (KnownCurrency k : CurrencyDetector.found()) {
                if (com.tac5studios.elementseconomy.bridge.Bridges.providing(k.modId())) continue;
                ElementsEconomy.LOGGER.warn("[Economy] {} was found but its balances can't be read. Using digital money instead.", k.name());
            }
        }
        if (Features.on(Features.CURRENCY_USE_DETECTED) && detected != null) return detected;
        if (Features.on(Features.CURRENCY_DIGITAL)) return digitalCurrency;
        return detected;
    }

    /**
     * First non-digital currency, in the order the detector found the mods.
     * Mods that keep money in their own data are only used when their adapter connected.
     */
    private Currency firstDetected() {
        for (KnownCurrency k : CurrencyDetector.found()) {
            // Mods that Elements: Economy is the money provider for hold our own money, not theirs.
            if (com.tac5studios.elementseconomy.bridge.Bridges.providing(k.modId())) continue;
            boolean coinMod = k.kind() == com.tac5studios.elementseconomy.currency.CurrencyKind.ITEMS;
            for (CurrencyBackend b : backends.values()) {
                if (b == digital || !b.currency().sourceMod().equals(k.modId())) continue;
                boolean external = b.currency().kind() == com.tac5studios.elementsvault.CurrencyKind.EXTERNAL;
                // A mod with its own balance only counts through that balance: its coins alone would show a wrong total.
                if (coinMod || external) return b.currency();
            }
        }
        // Currencies added by other mods through Elements: Vault.
        java.util.Set<String> known = new java.util.HashSet<>();
        for (KnownCurrency k : CurrencyDetector.found()) known.add(k.modId());
        for (CurrencyBackend b : backends.values()) {
            if (b != digital && !known.contains(b.currency().sourceMod())) return b.currency();
        }
        return null;
    }

    private void startingBalance(ServerPlayer p) {
        if (!Features.on(Features.ECONOMY, Features.ECO_STARTING_BALANCE)) return;
        if (!activeCurrencies().contains(digitalCurrency) || digital.hasAccount(p.getUUID())) return;
        BigInteger start = Amounts.parseOrZero(EconomyConfig.STARTING_BALANCE.get(), digitalCurrency.decimals()).orElse(BigInteger.ZERO);
        digital.set(p.getUUID(), start, Cause.system("starting balance"));
        if (start.signum() > 0) TransactionLog.add("START", p.getGameProfile().getName() + " " + digitalCurrency.format(start).getString());
    }

    // ---------- currencies ----------

    @Override
    public String providerName() {
        return "Elements: Economy";
    }

    @Override
    public Currency primaryCurrency() {
        return primary;
    }

    @Override
    public List<Currency> activeCurrencies() {
        if (primary == null) return List.of();
        if (!Features.on(Features.CURRENCY_MULTIPLE)) return List.of(primary);
        List<Currency> out = new ArrayList<>();
        out.add(primary);
        for (CurrencyBackend b : backends.values()) {
            if (!b.currency().equals(primary)) out.add(b.currency());
        }
        return out;
    }

    @Override
    public Collection<Currency> currencies() {
        return backends.values().stream().map(CurrencyBackend::currency).toList();
    }

    @Override
    public Optional<Currency> currency(ResourceLocation id) {
        CurrencyBackend b = backends.get(id);
        return b == null ? Optional.empty() : Optional.of(b.currency());
    }

    public DigitalCurrency digitalCurrency() {
        return digitalCurrency;
    }

    private CurrencyBackend backend(Currency c) {
        return backends.get(c.id());
    }

    /** Typed text -> money in that currency. Empty when it isn't a valid amount. */
    public Optional<Money> parse(Currency c, String text) {
        return c.parse(text).map(c::of);
    }

    public String format(Money m) {
        return m.currency().format(m.amount()).getString();
    }

    // ---------- reading ----------

    @Override
    public Money balance(UUID player, Currency currency) {
        CurrencyBackend b = backend(currency);
        if (b == null) return currency.zero();
        try {
            return currency.of(b.balance(player));
        } catch (RuntimeException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] Could not read {} balance: {}", currency.id(), ex.toString());
            return currency.zero();
        }
    }

    @Override
    public List<Holding> top(Currency currency, int limit) {
        CurrencyBackend b = backend(currency);
        if (b == null || !currency.can(Capability.LIST_ALL)) return List.of();
        return b.top(limit);
    }

    // ---------- dry runs ----------

    @Override
    public Result canWithdraw(UUID player, Money money) {
        Result bad = check(player, money);
        if (bad != null) return bad;
        if (!has(player, money)) return Result.fail(Result.Reason.INSUFFICIENT_FUNDS, money, Component.literal("Not enough money"));
        return Result.allowed(money);
    }

    @Override
    public Result canDeposit(UUID player, Money money) {
        Result bad = check(player, money);
        if (bad != null) return bad;
        if (!backend(money.currency()).canDeposit(player, money.amount())) {
            return Result.fail(Result.Reason.NOT_SUPPORTED, money, Component.literal("Can't receive this right now"));
        }
        return Result.allowed(money);
    }

    /** Shared checks. Null when everything is fine. */
    private Result check(UUID player, Money money) {
        if (money.isNegative()) return Result.fail(Result.Reason.INVALID_AMOUNT, money);
        CurrencyBackend b = backend(money.currency());
        if (b == null) return Result.fail(Result.Reason.NOT_SUPPORTED, money, Component.literal("Unknown currency"));
        if (!isOnline(player) && !money.currency().can(Capability.OFFLINE_WRITE) && !money.currency().can(Capability.ITEM_BACKED)) {
            return Result.fail(Result.Reason.OFFLINE_NOT_SUPPORTED, money, Component.literal("Player must be online"));
        }
        return null;
    }

    public static boolean isOnline(UUID player) {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s != null && s.getPlayerList().getPlayer(player) != null;
    }

    // ---------- changes ----------

    @Override
    public Result withdraw(UUID player, Money money, Cause cause) {
        return change(TransactionEvent.Type.WITHDRAW, player, null, money, cause,
                m -> backend(m.currency()).withdraw(player, m.amount(), cause));
    }

    /**
     * The part of an amount that can actually be paid out. For coin currencies, value smaller than the
     * smallest coin can't be handed over, so it stays where it is (till, collection box) instead of vanishing.
     */
    public static BigInteger payable(Currency currency, BigInteger amount) {
        return currency instanceof ItemCurrency ic ? ic.payable(amount) : amount.max(BigInteger.ZERO);
    }

    /**
     * An amount from a config file. Unlike typed prices, values that aren't whole coins are rounded up
     * instead of being ignored (so a fee of 10 with a smallest coin of 50 becomes 50, not free).
     */
    public static java.util.Optional<BigInteger> configAmount(Currency currency, String text) {
        return Amounts.parse(text, currency.decimals()).map(v -> roundUp(currency, v));
    }

    /** For coin currencies, the next amount that whole coins can pay (prices, bids). Others unchanged. */
    public static BigInteger roundUp(Currency currency, BigInteger amount) {
        return currency instanceof ItemCurrency ic && amount.signum() > 0 ? ic.roundUp(amount) : amount;
    }

    @Override
    public Result deposit(UUID player, Money money, Cause cause) {
        return change(TransactionEvent.Type.DEPOSIT, player, null, money, cause,
                m -> backend(m.currency()).deposit(player, m.amount(), cause));
    }

    @Override
    public Result set(UUID player, Money money, Cause cause) {
        return change(TransactionEvent.Type.SET, player, null, money, cause,
                m -> backend(m.currency()).set(player, m.amount(), cause));
    }

    @Override
    public Result transfer(UUID from, UUID to, Money money, Cause cause) {
        if (from.equals(to)) return Result.fail(Result.Reason.NOT_SUPPORTED, money, Component.literal("Same player"));
        Result bad = check(to, money);
        if (bad != null) return bad;
        return change(TransactionEvent.Type.TRANSFER, from, to, money, cause, m -> {
            CurrencyBackend b = backend(m.currency());
            if (!b.canDeposit(to, m.amount())) {
                return Result.fail(Result.Reason.OFFLINE_NOT_SUPPORTED, m, Component.literal("Player must be online"));
            }
            Result out = b.withdraw(from, m.amount(), cause);
            if (!out.success()) return out;
            Result in = b.deposit(to, m.amount(), cause);
            if (!in.success()) {
                b.deposit(from, m.amount(), Cause.system("refund: " + in.reason())); // put it back
                return in;
            }
            return out;
        });
    }

    /** Runs one change with checks, events, log and history around it. */
    private Result change(TransactionEvent.Type type, UUID player, UUID other, Money money, Cause cause, Function<Money, Result> op) {
        if (money.isNegative() || (type != TransactionEvent.Type.SET && money.isZero())) {
            return Result.fail(Result.Reason.INVALID_AMOUNT, money);
        }
        Result bad = check(player, money);
        if (bad != null) return bad;

        boolean events = Features.on(Features.API, Features.API_EVENTS);
        Money before = balance(player, money.currency());
        if (events) {
            TransactionEvent.Pre pre = NeoForge.EVENT_BUS.post(new TransactionEvent.Pre(type, player, other, money, cause));
            if (pre.isCanceled()) return Result.fail(Result.Reason.CANCELLED, money, Component.literal("Cancelled"));
            money = pre.amount();
        }

        Result r;
        try {
            r = op.apply(money);
        } catch (RuntimeException ex) {
            ElementsEconomy.LOGGER.error("[Economy] {} of {} failed.", type, money, ex);
            r = Result.fail(Result.Reason.BACKEND_ERROR, money, Component.literal("Something went wrong"));
        }
        if (!r.success()) return r;

        log(type, player, other, money, cause);
        history(type, player, other, money, cause);
        if (events) {
            NeoForge.EVENT_BUS.post(new TransactionEvent.Post(type, player, other, money, cause, r));
            NeoForge.EVENT_BUS.post(new BalanceChangedEvent(player, before, balance(player, money.currency())));
            if (other != null) {
                Money otherAfter = balance(other, money.currency());
                NeoForge.EVENT_BUS.post(new BalanceChangedEvent(other, otherAfter.subtract(money), otherAfter));
            }
        }
        return r;
    }

    private void log(TransactionEvent.Type type, UUID player, UUID other, Money money, Cause cause) {
        StringBuilder line = new StringBuilder(Accounts.name(player));
        if (other != null) line.append(" -> ").append(Accounts.name(other));
        line.append(' ').append(format(money)).append(" (").append(money.currency().id()).append(')');
        line.append(" [").append(cause.type().name().toLowerCase());
        if (!cause.source().isEmpty()) line.append(' ').append(cause.source());
        if (cause.actor() != null && !cause.actor().equals(player)) line.append(" by ").append(Accounts.name(cause.actor()));
        line.append(']');
        if (!cause.reason().isEmpty()) line.append(" \"").append(cause.reason()).append('"');
        TransactionLog.add(type == TransactionEvent.Type.TRANSFER ? "PAY" : type.name(), line.toString());
    }

    private void history(TransactionEvent.Type type, UUID player, UUID other, Money money, Cause cause) {
        String kind = cause.type().name().toLowerCase();
        switch (type) {
            case TRANSFER -> {
                Accounts.record(player, kind, other, money, cause.reason(), false);
                Accounts.record(other, kind, player, money, cause.reason(), true);
            }
            case DEPOSIT -> Accounts.record(player, kind, cause.actor(), money, cause.reason(), true);
            case WITHDRAW -> Accounts.record(player, kind, cause.actor(), money, cause.reason(), false);
            case SET -> Accounts.record(player, kind + " set", cause.actor(), money, cause.reason(), true);
        }
    }

    // ---------- converting ----------

    /** What one smallest unit of a currency is worth in whole digital money. Empty when no rate is set. */
    public Optional<BigDecimal> rate(Currency c) {
        if (c.equals(digitalCurrency)) return Optional.of(BigDecimal.ONE.movePointLeft(digitalCurrency.decimals()));
        return rateFor(c.id().toString());
    }

    /** The rates line for an id (a currency, or a migration source such as impactor:dollars). */
    public static Optional<BigDecimal> rateFor(String id) {
        for (String line : EconomyConfig.RATES.get()) {
            int eq = line.indexOf('=');
            if (eq <= 0 || !line.substring(0, eq).trim().equals(id)) continue;
            try {
                BigDecimal r = new BigDecimal(line.substring(eq + 1).trim());
                return r.signum() > 0 ? Optional.of(r) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty(); // bad line
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Money> convert(Money money, Currency target) {
        if (money.currency().equals(target)) return Optional.of(money);
        Optional<BigDecimal> from = rate(money.currency());
        Optional<BigDecimal> to = rate(target);
        if (from.isEmpty() || to.isEmpty()) return Optional.empty();
        BigDecimal worth = new BigDecimal(money.amount()).multiply(from.get());
        BigInteger out = worth.divide(to.get(), 0, RoundingMode.DOWN).toBigInteger();
        return Optional.of(target.of(out));
    }

    @Override
    public List<ItemStack> toItems(Money money) {
        if (money.currency() instanceof ItemCurrency ic) return Coins.stacks(ic.namespace(), Amounts.toLong(money.amount()));
        return List.of();
    }

    @Override
    public Money fromItems(Collection<ItemStack> stacks, Currency currency) {
        if (currency instanceof ItemCurrency ic) return currency.of(Coins.valueOf(stacks, ic.namespace()));
        return currency.zero();
    }

    @Override
    public boolean isCurrencyItem(ItemStack stack) {
        return !stack.isEmpty() && Coins.currencyOf(stack.getItem()) != null;
    }
}
