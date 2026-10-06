package com.tac5studios.elementseconomy.currency;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns an amount of money into what the shop and auction screens show.
 * Item currencies use the coins and values from currency_values.toml (through {@link CurrencyDetector}),
 * so a change to that file, a newly detected mod, or a confirmed vanilla item shows up in every screen.
 *
 * Currency ids: {@link #DIGITAL} for built-in digital money, or an item-currency namespace such as "aiycoin".
 */
public final class CurrencyDisplay {

    public static final String DIGITAL = "elements_economy:digital";

    /** One coin type in a breakdown. */
    public record Coin(Item item, long count, long value) {}

    private static Component digitalName = Component.literal("Coins");
    private static Item digitalIcon = Items.PAPER;

    private CurrencyDisplay() {}

    /** Set from ui.toml: the name and icon used for digital money. */
    public static void setDigital(Component name, Item icon) {
        digitalName = name;
        digitalIcon = icon;
    }

    /** True when this currency can be shown (digital, or an item currency with counted coins). */
    public static boolean isKnown(String currency) {
        return DIGITAL.equals(currency) || !CurrencyDetector.denominations(currency).isEmpty();
    }

    /**
     * Fewest coins for an amount, largest coin first. Any value too small for the smallest coin
     * is left out of the list and reported by {@link #remainder}.
     */
    public static List<Coin> breakdown(String currency, long amount) {
        List<Coin> out = new ArrayList<>();
        long left = Math.max(0, amount);
        for (Map.Entry<Item, Long> d : CurrencyDetector.denominations(currency)) {
            long value = d.getValue();
            if (value <= 0 || left < value) {
                continue;
            }
            long count = left / value;
            left -= count * value;
            out.add(new Coin(d.getKey(), count, value));
        }
        return out;
    }

    /** Value left over after {@link #breakdown} (0 when the coins cover the amount exactly). */
    public static long remainder(String currency, long amount) {
        long used = 0;
        for (Coin c : breakdown(currency, amount)) {
            used += c.count() * c.value();
        }
        return Math.max(0, amount - used);
    }

    /** Readable text, e.g. "2 Gold Coin, 40 Silver Coin" or "1,250 Coins". Item names are translated on the client. */
    public static Component format(String currency, long amount) {
        if (DIGITAL.equals(currency)) {
            return Component.literal(String.format("%,d ", amount)).append(digitalName);
        }
        List<Coin> coins = breakdown(currency, amount);
        if (coins.isEmpty()) {
            return Component.literal("0");
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < coins.size(); i++) {
            Coin c = coins.get(i);
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(Component.literal(String.format("%,d ", c.count()))).append(c.item().getDescription());
        }
        long rest = remainder(currency, amount);
        if (rest > 0) {
            out.append(Component.literal(" (+" + rest + " too small for a coin)"));
        }
        return out;
    }

    /**
     * Item stacks for the price columns (shops: columns 4–5, auction house: 5–6).
     * Shows the largest coin types, one per slot. Counts over 64 show as one coin with the count in its name.
     * If there are more coin types than slots, the last slot's lore lists the rest.
     * The caller adds the column tooltip lines with {@link #withLore}.
     */
    public static List<ItemStack> priceSlots(String currency, long amount, int slots) {
        List<ItemStack> out = new ArrayList<>();
        if (slots <= 0) {
            return out;
        }
        if (DIGITAL.equals(currency)) {
            ItemStack s = new ItemStack(digitalIcon);
            s.set(DataComponents.CUSTOM_NAME, plain(format(currency, amount)).withStyle(ChatFormatting.YELLOW));
            out.add(s);
            return out;
        }
        List<Coin> coins = breakdown(currency, amount);
        for (int i = 0; i < coins.size() && i < slots; i++) {
            Coin c = coins.get(i);
            ItemStack s = new ItemStack(c.item());
            if (c.count() <= 64) {
                s.setCount((int) c.count());
            } else {
                s.set(DataComponents.CUSTOM_NAME, plain(Component.literal(String.format("%,d x ", c.count()))
                        .append(c.item().getDescription())));
            }
            if (i == slots - 1 && coins.size() > slots) {
                List<Component> more = new ArrayList<>();
                more.add(plain(Component.literal("Also:")).withStyle(ChatFormatting.GRAY));
                for (int j = slots; j < coins.size(); j++) {
                    Coin m = coins.get(j);
                    more.add(plain(Component.literal(String.format("%,d ", m.count())).append(m.item().getDescription()))
                            .withStyle(ChatFormatting.GRAY));
                }
                s.set(DataComponents.LORE, new ItemLore(more));
            }
            out.add(s);
        }
        return out;
    }

    /** Adds tooltip lines under whatever lore the stack already has. */
    public static ItemStack withLore(ItemStack stack, List<Component> lines) {
        ItemLore old = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> all = new ArrayList<>(old.lines());
        for (Component c : lines) {
            all.add(plain(c));
        }
        stack.set(DataComponents.LORE, new ItemLore(all));
        return stack;
    }

    /** Item currency id for an item, or null when the item is not counted as money. */
    public static String currencyOf(Item item) {
        if (!CurrencyDetector.isMoney(item)) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace();
    }

    /** Removes the default italic that custom names and lore get. */
    private static MutableComponent plain(Component c) {
        return c.copy().withStyle(style -> style.withItalic(false));
    }
}
