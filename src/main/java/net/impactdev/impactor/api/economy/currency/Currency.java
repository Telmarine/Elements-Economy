// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.currency;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;

import java.math.BigDecimal;
import java.util.Locale;

public interface Currency {
    Key key();
    Component singular();
    Component plural();
    Component symbol();
    CurrencyFormatting formatting();
    BigDecimal defaultAccountBalance();
    int decimals();
    boolean primary();
    TriState transferable();
    Component format(BigDecimal amount, boolean condensed, Locale locale);

    record CurrencyFormatting(String condensed, String expanded) {}
}
