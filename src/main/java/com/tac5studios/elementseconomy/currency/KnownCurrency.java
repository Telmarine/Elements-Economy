package com.tac5studios.elementseconomy.currency;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One supported currency mod.
 *
 * @param modId    mod ID from neoforge.mods.toml
 * @param name     display name for logs
 * @param kind     how the mod keeps money
 * @param markers  class names that prove it is this mod (any one is enough). Empty = mod ID is enough.
 * @param items    item path -> default value (ITEMS kind, or API mods that also have coins). 0 = owner must set it.
 * @param note     short storage note shown in the detection log
 */
public record KnownCurrency(String modId, String name, CurrencyKind kind, List<String> markers,
                            Map<String, Long> items, String note) {

    public KnownCurrency {
        markers = List.copyOf(markers);
        items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public boolean hasItems() {
        return !items.isEmpty();
    }
}
