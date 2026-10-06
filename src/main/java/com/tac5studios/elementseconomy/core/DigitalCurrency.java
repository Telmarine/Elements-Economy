package com.tac5studios.elementseconomy.core;

import com.tac5studios.elementseconomy.config.EconomyConfig;
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

/** Elements: Economy's own money, stored as numbers. Name, symbol and decimals come from economy.toml. */
public final class DigitalCurrency implements Currency {

    public static final ResourceLocation ID = ResourceLocation.parse(CurrencyDisplay.DIGITAL);

    private static final Set<Capability> CAPS = EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL);

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component name() {
        return Component.literal(EconomyConfig.DIGITAL_PLURAL.get());
    }

    @Override
    public String symbol() {
        return EconomyConfig.DIGITAL_SYMBOL.get();
    }

    @Override
    public int decimals() {
        return EconomyConfig.DIGITAL_DECIMALS.get();
    }

    @Override
    public CurrencyKind kind() {
        return CurrencyKind.DIGITAL;
    }

    @Override
    public String sourceMod() {
        return ID.getNamespace();
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPS;
    }

    @Override
    public Component format(BigInteger amount) {
        int d = decimals();
        boolean one = amount.equals(BigInteger.TEN.pow(d));
        String name = one ? EconomyConfig.DIGITAL_NAME.get() : EconomyConfig.DIGITAL_PLURAL.get();
        return Component.literal(symbol() + Amounts.format(amount, d) + " " + name);
    }

    @Override
    public Optional<BigInteger> parse(String text) {
        String s = text;
        if (!symbol().isEmpty() && s.startsWith(symbol())) s = s.substring(symbol().length());
        return Amounts.parse(s, decimals());
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(iconItem());
    }

    public static Item iconItem() {
        ResourceLocation id = ResourceLocation.tryParse(EconomyConfig.DIGITAL_ICON.get());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return Items.PAPER;
        return BuiltInRegistries.ITEM.get(id);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Currency c && c.id().equals(ID);
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return ID.toString();
    }
}
