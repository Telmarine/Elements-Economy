// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.transactions;

import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.impactdev.impactor.api.economy.transactions.details.EconomyTransactionType;
import net.kyori.adventure.text.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;

public interface EconomyTransaction {
    Currency currency();
    Account account();
    BigDecimal amount();
    EconomyTransactionType type();
    EconomyResultType result();
    Supplier<Component> message();
    Instant timestamp();
}
