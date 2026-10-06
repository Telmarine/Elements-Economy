package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.CurrencyDisplay;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.CurrencyKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Coins from a currency mod (or confirmed vanilla items). One per item namespace, e.g. aiycoin:coins.
 * Coin values come from currency_values.toml; the smallest coin is usually worth 1.
 */
public final class ItemCurrency implements Currency {

    private static final Set<Capability> CAPS = EnumSet.of(Capability.ITEM_BACKED);

    private final String namespace;
    private final ResourceLocation id;
    private final String displayName;

    public ItemCurrency(String namespace, String displayName) {
        this.namespace = namespace;
        this.id = ResourceLocation.fromNamespaceAndPath(namespace, "coins");
        this.displayName = displayName;
    }

    /** The item namespace, e.g. "aiycoin". */
    public String namespace() {
        return namespace;
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public Component name() {
        return Component.literal(displayName);
    }

    @Override
    public String symbol() {
        return "";
    }

    @Override
    public int decimals() {
        return 0;
    }

    @Override
    public CurrencyKind kind() {
        return CurrencyKind.ITEMS;
    }

    @Override
    public String sourceMod() {
        return namespace;
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPS;
    }

    @Override
    public Component format(BigInteger amount) {
        return CurrencyDisplay.format(namespace, Amounts.toLong(amount));
    }

    @Override
    public Optional<BigInteger> parse(String text) {
        return Amounts.parse(text, 0);
    }

    @Override
    public ItemStack icon() {
        List<Map.Entry<Item, Long>> d = CurrencyDetector.denominations(namespace);
        return new ItemStack(d.isEmpty() ? Items.GOLD_NUGGET : d.get(0).getKey());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Currency c && c.id().equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
