// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy;

import com.google.common.collect.Multimap;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.currency.CurrencyProvider;
import net.impactdev.impactor.api.services.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface EconomyService extends Service {
    CurrencyProvider currencies();
    CompletableFuture<Boolean> hasAccount(Currency currency, UUID uuid);
    CompletableFuture<Account> account(Currency currency, UUID uuid);
    CompletableFuture<Account> account(Currency currency, UUID uuid, Account.AccountModifier modifier);
    CompletableFuture<Multimap<Currency, Account>> accounts();
    CompletableFuture<Void> deleteAccount(Currency currency, UUID uuid);
    CompletableFuture<Void> save(Account account);
}
