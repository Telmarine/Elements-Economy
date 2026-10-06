package com.tac5studios.elementseconomy.bridge.impactor;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.tac5studios.elementseconomy.bridge.Bridges;
import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Amounts;
import com.tac5studios.elementseconomy.core.DigitalCurrency;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.currency.CurrencyProvider;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.EconomyTransferTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.impactdev.impactor.api.economy.transactions.details.EconomyTransactionType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Impactor's economy service, answered by Elements: Economy. Impactor currencies are views of
 * Elements: Economy currencies (same id, e.g. elements_economy:digital); every balance change goes
 * through the economy core, so logs, history, events and limits all apply.
 */
final class ImpactorEconomy implements EconomyService {

    private static final Cause CAUSE = Cause.plugin("impactor");

    private final Map<ResourceLocation, View> views = new ConcurrentHashMap<>();
    private final Provider provider = new Provider();

    @Override
    public String name() {
        return "Elements: Economy";
    }

    @Override
    public CurrencyProvider currencies() {
        return provider;
    }

    private static Economy economy() {
        Economy e = Economy.get();
        if (e == null || !Economy.running()) throw new IllegalStateException("Elements: Economy is not running");
        return e;
    }

    private View view(com.tac5studios.elementsvault.Currency c) {
        return views.computeIfAbsent(c.id(), id -> new View(id));
    }

    /** Our currency for an Impactor currency (only our own views are accepted). */
    private static Optional<com.tac5studios.elementsvault.Currency> ours(Currency c) {
        if (!(c instanceof View v)) return Optional.empty();
        return economy().currency(v.id);
    }

    @Override
    public CompletableFuture<Boolean> hasAccount(Currency currency, UUID uuid) {
        return CompletableFuture.completedFuture(ours(currency).isPresent());
    }

    @Override
    public CompletableFuture<Account> account(Currency currency, UUID uuid) {
        return CompletableFuture.completedFuture(new PlayerAccount(currency instanceof View v ? v : (View) provider.primary(), uuid));
    }

    @Override
    public CompletableFuture<Account> account(Currency currency, UUID uuid, Account.AccountModifier modifier) {
        return account(currency, uuid); // virtual accounts are kept like any other account
    }

    @Override
    public CompletableFuture<Multimap<Currency, Account>> accounts() {
        return CompletableFuture.supplyAsync(() -> Bridges.onServer(() -> {
            Multimap<Currency, Account> out = ArrayListMultimap.create();
            for (com.tac5studios.elementsvault.Currency c : economy().activeCurrencies()) {
                if (!c.can(Capability.LIST_ALL)) continue;
                View v = view(c);
                for (Holding h : economy().top(c, Integer.MAX_VALUE)) out.put(v, new PlayerAccount(v, h.player()));
            }
            return out;
        }));
    }

    @Override
    public CompletableFuture<Void> deleteAccount(Currency currency, UUID uuid) {
        new PlayerAccount(currency instanceof View v ? v : (View) provider.primary(), uuid).set(BigDecimal.ZERO);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> save(Account account) {
        return CompletableFuture.completedFuture(null); // every change is saved as it happens
    }

    // ---------- currencies ----------

    private final class Provider implements CurrencyProvider {

        @Override
        public Currency primary() {
            return view(economy().primaryCurrency());
        }

        @Override
        public Optional<Currency> currency(Key key) {
            ResourceLocation id = ResourceLocation.tryParse(key.asString());
            if (id == null) return Optional.empty();
            return economy().currency(id).filter(c -> economy().activeCurrencies().contains(c)).map(c -> (Currency) view(c));
        }

        @Override
        public Set<Currency> registered() {
            // SDM Economy lists every currency registered here. With bridges.sdm_currency off, hide them from it.
            if (!Features.on(Features.BRIDGES, Features.BRIDGE_SDM) && calledBySdm()) return Set.of();
            if (!Economy.running()) return Set.of();
            Set<Currency> out = new LinkedHashSet<>();
            for (com.tac5studios.elementsvault.Currency c : economy().activeCurrencies()) out.add(view(c));
            return out;
        }

        @Override
        public CompletableFuture<Boolean> register(Currency currency) {
            return CompletableFuture.completedFuture(false); // currencies come from Elements: Economy's own setup
        }

        private boolean calledBySdm() {
            return StackWalker.getInstance().walk(s -> s.limit(8).anyMatch(f -> f.getClassName().startsWith("net.sixik.")));
        }
    }

    /** An Elements: Economy currency, as Impactor sees it. */
    private static final class View implements Currency {
        private final ResourceLocation id;

        View(ResourceLocation id) {
            this.id = id;
        }

        private com.tac5studios.elementsvault.Currency c() {
            return economy().currency(id).orElseThrow(() -> new IllegalStateException("Currency " + id + " is gone"));
        }

        @Override
        public Key key() {
            return Key.key(id.getNamespace(), id.getPath());
        }

        @Override
        public Component singular() {
            return Component.text(c().name().getString());
        }

        @Override
        public Component plural() {
            if (c() instanceof DigitalCurrency) return Component.text(EconomyConfig.DIGITAL_PLURAL.get());
            return singular();
        }

        @Override
        public Component symbol() {
            String s = c().symbol();
            return Component.text(s == null || s.isEmpty() ? "◎" : s);
        }

        @Override
        public CurrencyFormatting formatting() {
            return new CurrencyFormatting("<amount>", "<amount>");
        }

        @Override
        public BigDecimal defaultAccountBalance() {
            if (!(c() instanceof DigitalCurrency d) || !Features.on(Features.ECONOMY, Features.ECO_STARTING_BALANCE)) return BigDecimal.ZERO;
            BigInteger start = Amounts.parseOrZero(EconomyConfig.STARTING_BALANCE.get(), d.decimals()).orElse(BigInteger.ZERO);
            return Bridges.fromUnits(start, d.decimals());
        }

        @Override
        public int decimals() {
            return c().decimals();
        }

        @Override
        public boolean primary() {
            return economy().primaryCurrency().id().equals(id);
        }

        @Override
        public TriState transferable() {
            return TriState.TRUE;
        }

        @Override
        public Component format(BigDecimal amount, boolean condensed, Locale locale) {
            com.tac5studios.elementsvault.Currency c = c();
            BigInteger units = Bridges.toUnits(amount.abs(), c.decimals());
            String text = economy().format(c.of(units));
            return Component.text(amount.signum() < 0 ? "-" + text : text);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof View v && v.id.equals(id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "ElementsEconomyCurrency[" + id + "]";
        }
    }

    // ---------- accounts ----------

    private static final class PlayerAccount implements Account {
        private final View view;
        private final UUID owner;

        PlayerAccount(View view, UUID owner) {
            this.view = view;
            this.owner = owner;
        }

        @Override
        public Currency currency() {
            return view;
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public boolean virtual() {
            return false;
        }

        @Override
        public BigDecimal balance() {
            return Bridges.onServer(() -> {
                com.tac5studios.elementsvault.Currency c = view.c();
                return Bridges.fromUnits(economy().balance(owner, c).amount(), c.decimals());
            });
        }

        private EconomyTransaction run(EconomyTransactionType type, BigDecimal amount) {
            return Bridges.onServer(() -> {
                com.tac5studios.elementsvault.Currency c = view.c();
                BigInteger units = Bridges.toUnits(amount, c.decimals());
                if (units.signum() < 0) return new Transaction(this, type, amount, EconomyResultType.INVALID);
                Money m = c.of(units);
                Result r = switch (type) {
                    case DEPOSIT -> economy().deposit(owner, m, CAUSE);
                    case WITHDRAW -> economy().withdraw(owner, m, CAUSE);
                    default -> economy().set(owner, m, CAUSE);
                };
                return new Transaction(this, type, amount, result(r));
            });
        }

        @Override
        public EconomyTransaction set(BigDecimal amount) {
            return run(EconomyTransactionType.SET, amount);
        }

        @Override
        public EconomyTransaction withdraw(BigDecimal amount) {
            return run(EconomyTransactionType.WITHDRAW, amount);
        }

        @Override
        public EconomyTransaction deposit(BigDecimal amount) {
            return run(EconomyTransactionType.DEPOSIT, amount);
        }

        @Override
        public EconomyTransaction reset() {
            EconomyTransaction t = run(EconomyTransactionType.SET, view.defaultAccountBalance());
            return new Transaction(this, EconomyTransactionType.RESET, t.amount(), t.result());
        }

        @Override
        public EconomyTransferTransaction transfer(Account to, BigDecimal amount) {
            return Bridges.onServer(() -> {
                if (!(to.currency() instanceof View tv) || !tv.equals(view)) {
                    return new Transfer(this, to, amount, EconomyResultType.INVALID);
                }
                com.tac5studios.elementsvault.Currency c = view.c();
                BigInteger units = Bridges.toUnits(amount, c.decimals());
                if (units.signum() <= 0) return new Transfer(this, to, amount, EconomyResultType.INVALID);
                Result r = economy().transfer(owner, to.owner(), c.of(units), CAUSE);
                return new Transfer(this, to, amount, result(r));
            });
        }
    }

    private static EconomyResultType result(Result r) {
        if (r.success()) return EconomyResultType.SUCCESS;
        return switch (r.reason()) {
            case INSUFFICIENT_FUNDS -> EconomyResultType.NOT_ENOUGH_FUNDS;
            case INVALID_AMOUNT -> EconomyResultType.INVALID;
            case CANCELLED -> EconomyResultType.CANCELLED;
            case LIMIT -> EconomyResultType.NO_REMAINING_SPACE;
            default -> EconomyResultType.FAILED;
        };
    }

    // ---------- results ----------

    private record Transaction(Account account, EconomyTransactionType type, BigDecimal amount,
                               EconomyResultType result, Instant timestamp) implements EconomyTransaction {
        Transaction(Account account, EconomyTransactionType type, BigDecimal amount, EconomyResultType result) {
            this(account, type, amount, result, Instant.now());
        }

        @Override
        public Currency currency() {
            return account.currency();
        }

        @Override
        public Supplier<Component> message() {
            return null;
        }
    }

    private record Transfer(Account from, Account to, BigDecimal amount, EconomyResultType result)
            implements EconomyTransferTransaction {
        @Override
        public Currency currency() {
            return from.currency();
        }

        @Override
        public Supplier<Component> message() {
            return null;
        }
    }
}
