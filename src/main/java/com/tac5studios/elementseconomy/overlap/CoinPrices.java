package com.tac5studios.elementseconomy.overlap;

import com.tac5studios.elementseconomy.core.Coins;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.core.ItemCurrency;
import com.tac5studios.elementseconomy.currency.CurrencyDetector;
import com.tac5studios.elementseconomy.currency.SwitchOver;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts item prices (coin stacks in a shop slot or a trade cost) through the currency switch history.
 * Only coins of a switch's old currency change, and only into another coin currency: item prices never
 * become digital, and vanilla or other items are left alone.
 */
public final class CoinPrices {

    /** Saved on converted shops and NPCs: how many switches their prices have been through. */
    public static final String MARKER = "elements_economy_switch";

    private CoinPrices() {}

    /**
     * Run the switches from {@code start} on these costs. Each stack is one price slot; the result
     * never uses more than {@code slots} slots. Returns empty when nothing changed.
     */
    public static Optional<List<ItemStack>> apply(List<ItemStack> costs, int slots, List<SwitchOver.Step> steps, int start) {
        Economy e = Economy.get();
        if (e == null) return Optional.empty();
        List<ItemStack> now = new ArrayList<>();
        for (ItemStack s : costs) if (!s.isEmpty()) now.add(s.copy());
        boolean changed = false;
        for (int i = Math.max(0, start); i < steps.size(); i++) {
            SwitchOver.Step step = steps.get(i);
            String fromNs = namespace(e, step.from());
            String toNs = namespace(e, step.to());
            if (fromNs == null || toNs == null) continue; // not coin to coin: left alone
            long value = 0;
            List<ItemStack> keep = new ArrayList<>();
            for (ItemStack s : now) {
                if (fromNs.equals(Coins.currencyOf(s.getItem()))) value += s.getCount() * CurrencyDetector.valueOf(s.getItem());
                else keep.add(s);
            }
            if (value <= 0) continue;
            int free = slots - keep.size();
            if (free <= 0) continue;
            BigInteger target = step.convert(BigInteger.valueOf(value)).max(BigInteger.ONE);
            List<ItemStack> coins = fit(toNs, target.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(), free);
            if (coins.isEmpty()) continue;
            keep.addAll(coins);
            now = keep;
            changed = true;
        }
        return changed ? Optional.of(now) : Optional.empty();
    }

    /** True when any of these stacks is a coin of the currency with this id. */
    public static boolean hasCoinsOf(List<ItemStack> costs, ResourceLocation currencyId) {
        Economy e = Economy.get();
        String ns = e == null ? null : namespace(e, currencyId.toString());
        if (ns == null) return false;
        for (ItemStack s : costs) if (!s.isEmpty() && ns.equals(Coins.currencyOf(s.getItem()))) return true;
        return false;
    }

    private static String namespace(Economy e, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Optional<Currency> c = rl == null ? Optional.empty() : e.currency(rl);
        return c.isPresent() && c.get() instanceof ItemCurrency ic ? ic.namespace() : null;
    }

    /**
     * Coins worth (close to) {@code amount}, at most {@code slots} stacks, each within its max stack size.
     * Exact when it fits; otherwise the closest price those slots can show.
     */
    static List<ItemStack> fit(String ns, long amount, int slots) {
        List<Map.Entry<Item, Long>> denoms = Coins.denominations(ns); // largest first
        if (denoms.isEmpty() || amount <= 0 || slots <= 0) return List.of();

        // Exact, fewest coins.
        List<ItemStack> greedy = new ArrayList<>();
        long left = amount;
        for (Map.Entry<Item, Long> d : denoms) {
            long q = left / d.getValue();
            if (q <= 0) continue;
            int max = new ItemStack(d.getKey()).getMaxStackSize();
            if (q > max) break; // would need more than one stack of this coin
            greedy.add(new ItemStack(d.getKey(), (int) q));
            left -= q * d.getValue();
        }
        if (left == 0 && !greedy.isEmpty() && greedy.size() <= slots) return greedy;

        // Closest with one stack, or two when there is room.
        List<ItemStack> best = List.of();
        long bestErr = Long.MAX_VALUE;
        for (Map.Entry<Item, Long> d1 : denoms) {
            int max1 = new ItemStack(d1.getKey()).getMaxStackSize();
            long v1 = d1.getValue();
            long q1 = Math.min(max1, Math.max(1, Math.round((double) amount / v1)));
            long err1 = Math.abs(amount - q1 * v1);
            if (err1 < bestErr) {
                bestErr = err1;
                best = List.of(new ItemStack(d1.getKey(), (int) q1));
            }
            if (slots < 2) continue;
            long base = Math.min(max1, amount / v1);
            if (base <= 0) continue;
            long rest = amount - base * v1;
            for (Map.Entry<Item, Long> d2 : denoms) {
                if (d2.getValue() >= v1) continue;
                int max2 = new ItemStack(d2.getKey()).getMaxStackSize();
                long q2 = Math.min(max2, Math.round((double) rest / d2.getValue()));
                if (q2 <= 0) continue;
                long err = Math.abs(rest - q2 * d2.getValue());
                if (err < bestErr) {
                    bestErr = err;
                    best = List.of(new ItemStack(d1.getKey(), (int) base), new ItemStack(d2.getKey(), (int) q2));
                }
            }
        }
        return best;
    }
}
