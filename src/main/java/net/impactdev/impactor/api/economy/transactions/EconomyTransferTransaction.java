// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.transactions;

import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.kyori.adventure.text.Component;

import java.math.BigDecimal;
import java.util.function.Supplier;

public interface EconomyTransferTransaction {
    Currency currency();
    Account from();
    Account to();
    BigDecimal amount();
    EconomyResultType result();
    Supplier<Component> message();
}
