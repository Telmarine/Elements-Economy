// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.accounts;

import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.EconomyTransferTransaction;

import java.math.BigDecimal;
import java.util.UUID;

public interface Account {
    Currency currency();
    UUID owner();
    boolean virtual();
    BigDecimal balance();
    EconomyTransaction set(BigDecimal amount);
    EconomyTransaction withdraw(BigDecimal amount);
    EconomyTransaction deposit(BigDecimal amount);
    EconomyTransferTransaction transfer(Account to, BigDecimal amount);
    EconomyTransaction reset();

    interface AccountBuilder {}

    @FunctionalInterface
    interface AccountModifier {
        AccountBuilder modify(AccountBuilder builder);
    }
}
