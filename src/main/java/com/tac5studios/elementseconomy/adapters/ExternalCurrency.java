package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementseconomy.core.Amounts;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.CurrencyDisplay;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.CurrencyKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Money kept by another mod (its API, player data or a file).
 * When the mod also has coin items with values in currency_values.toml (e.g. Numismatics spurs),
 * amounts are shown as those coins; otherwise as "1,250.00 Euro".
 */
public final class ExternalCurrency implements Currency {

    private final ResourceLocation id;
    private final String modId;
    private final String name;
    private final int decimals;
    private final Set<Capability> caps;
    private final String coinNamespace;
    private final String iconId;

    /**
     * @param coinNamespace item namespace whose coin values match this money's smallest unit, or null
     * @param iconId        menu icon item id, or null for the largest coin / paper
     */
    public ExternalCurrency(String modId, String path, String name, int decimals, Set<Capability> caps,
                            String coinNamespace, String iconId) {
        this.id = ResourceLocation.fromNamespaceAndPath(modId, path);
        this.modId = modId;
        this.name = name;
        this.decimals = decimals;
        this.caps = caps.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(caps);
        this.coinNamespace = coinNamespace;
        this.iconId = iconId;
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public Component name() {
        return Component.literal(name);
    }

    @Override
    public String symbol() {
        return "";
    }

    @Override
    public int decimals() {
        return decimals;
    }

    @Override
    public CurrencyKind kind() {
        return CurrencyKind.EXTERNAL;
    }

    @Override
    public String sourceMod() {
        return modId;
    }

    @Override
    public Set<Capability> capabilities() {
        return caps;
    }

    @Override
    public Component format(BigInteger amount) {
        if (coinNamespace != null && decimals == 0 && !CurrencyDetector.denominations(coinNamespace).isEmpty()) {
            return CurrencyDisplay.format(coinNamespace, Amounts.toLong(amount));
        }
        return Component.literal(Amounts.format(amount, decimals) + " " + name);
    }

    @Override
    public Optional<BigInteger> parse(String text) {
        return Amounts.parse(text, decimals);
    }

    @Override
    public ItemStack icon() {
        if (iconId != null) {
            ResourceLocation rl = ResourceLocation.tryParse(iconId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) return new ItemStack(BuiltInRegistries.ITEM.get(rl));
        }
        if (coinNamespace != null) {
            var d = CurrencyDetector.denominations(coinNamespace);
            if (!d.isEmpty()) return new ItemStack(d.get(0).getKey());
        }
        Item fallback = Items.PAPER;
        return new ItemStack(fallback);
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
