package com.tac5studios.elementseconomy.overlap;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.currency.SwitchOver;
import com.tac5studios.elementsvault.shop.ConversionPlan;
import com.tac5studios.elementsvault.shop.CurrencyChange;
import com.tac5studios.elementsvault.shop.ShopBridge;
import com.tac5studios.elementsvault.shop.ShopKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A shop mod found by the overlap guard whose prices are coin items (Spud's Shops, Easy NPC).
 * Its shops can only be changed while loaded, so the bridge keeps a list of loaded ones, converts them
 * when the currency switches, and converts any it missed when they load or are used.
 * Each shop remembers how many switches it has been through ({@link CoinPrices#MARKER}).
 */
abstract class FoundShopBridge<T> implements ShopBridge {

    private final ResourceLocation id;
    private final String modId;
    private final Set<T> loaded = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    FoundShopBridge(String path, String modId) {
        this.id = ResourceLocation.fromNamespaceAndPath(ElementsEconomy.MOD_ID, path);
        this.modId = modId;
    }

    /** The marker tag that is saved with the shop. */
    abstract CompoundTag data(T shop);

    /** Save and resend the shop after a change. */
    abstract void changed(T shop);

    /** Every price on this shop, one list of cost stacks per price, with how many stacks each price may use. */
    abstract List<Price> prices(T shop);

    /** Write the converted prices back (same order as {@link #prices}; null = unchanged). */
    abstract void write(T shop, List<List<net.minecraft.world.item.ItemStack>> converted);

    record Price(List<net.minecraft.world.item.ItemStack> costs, int slots) {}

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public ShopKind kind() {
        return ShopKind.ITEM_PRICED;
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded(modId) && OverlapGuard.installed(modId)
                && Features.on(Features.OVERLAP_BRIDGE_FOUND)
                && Features.on(Features.SHOP_BRIDGE, Features.BRIDGE_ITEM_PRICED);
    }

    void track(T shop) {
        loaded.add(shop);
    }

    void untrack(T shop) {
        loaded.remove(shop);
    }

    private List<T> snapshot() {
        synchronized (loaded) {
            return new ArrayList<>(loaded);
        }
    }

    /** Apply every switch this shop hasn't had. True when a price changed. */
    boolean catchUp(T shop) {
        List<SwitchOver.Step> steps = SwitchOver.history();
        if (steps.isEmpty()) return false;
        CompoundTag tag = data(shop);
        int start = tag.contains(CoinPrices.MARKER) ? tag.getInt(CoinPrices.MARKER) : 0;
        if (start >= steps.size()) return false;
        boolean any = false;
        try {
            List<Price> prices = prices(shop);
            List<List<net.minecraft.world.item.ItemStack>> out = new ArrayList<>();
            for (Price p : prices) {
                var res = CoinPrices.apply(p.costs(), p.slots(), steps, start);
                out.add(res.orElse(null));
                any |= res.isPresent();
            }
            if (any) write(shop, out);
        } catch (RuntimeException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] {} price could not be converted: {}", modId, ex.toString());
            return false;
        }
        tag.putInt(CoinPrices.MARKER, steps.size());
        changed(shop);
        return any;
    }

    @Override
    public void onCurrencyChanged(CurrencyChange change, ConversionPlan plan) {
        int n = 0;
        for (T shop : snapshot()) {
            if (plan.preview()) {
                for (Price p : prices(shop)) {
                    if (CoinPrices.hasCoinsOf(p.costs(), change.from().id())) {
                        n++;
                        break;
                    }
                }
            } else if (catchUp(shop)) {
                n++;
            }
        }
        plan.report(id, n, 0); // shops that aren't loaded convert when they load
    }
}
