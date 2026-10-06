// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.currency;

import net.kyori.adventure.key.Key;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface CurrencyProvider {
    Currency primary();
    Optional<Currency> currency(Key key);
    Set<Currency> registered();
    CompletableFuture<Boolean> register(Currency currency);
}
