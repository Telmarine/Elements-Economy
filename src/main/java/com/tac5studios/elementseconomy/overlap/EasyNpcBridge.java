package com.tac5studios.elementseconomy.overlap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Easy NPC (easy_npc) trades: every NPC is a vanilla Merchant. Its saved trades come from
 * getTradingOffers() and are replaced with setTradingOffers(..) (TradingDataCapable), which also refreshes
 * what players see. Cost A and cost B are the price; coin costs follow coin-to-coin switches and may use
 * both slots when the other one holds coins too.
 */
final class EasyNpcBridge extends FoundShopBridge<Entity> {

    static final String MOD = "easy_npc";
    static final EasyNpcBridge INSTANCE = new EasyNpcBridge();

    private EasyNpcBridge() {
        super("easy_npc", MOD);
    }

    static boolean isNpc(Entity entity) {
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return MOD.equals(type.getNamespace()) && entity instanceof Merchant;
    }

    private static MerchantOffers offers(Entity npc) {
        try {
            Method m = npc.getClass().getMethod("getTradingOffers");
            Object v = m.invoke(npc);
            if (v instanceof MerchantOffers o) return o;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older Easy NPC: fall back to the vanilla merchant list
        }
        return ((Merchant) npc).getOffers();
    }

    private static void setOffers(Entity npc, MerchantOffers offers) {
        try {
            Method m = npc.getClass().getMethod("setTradingOffers", MerchantOffers.class);
            m.invoke(npc, offers);
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall back below
        }
        ((Merchant) npc).overrideOffers(offers);
    }

    @Override
    CompoundTag data(Entity npc) {
        return npc.getPersistentData();
    }

    @Override
    void changed(Entity npc) {
        // Entity data is saved with the chunk; setTradingOffers already synced the trades.
    }

    @Override
    List<Price> prices(Entity npc) {
        List<Price> out = new ArrayList<>();
        MerchantOffers offers = offers(npc);
        if (offers == null) return out;
        for (MerchantOffer o : offers) {
            List<ItemStack> costs = new ArrayList<>(2);
            costs.add(o.getBaseCostA());
            if (!o.getCostB().isEmpty()) costs.add(o.getCostB());
            out.add(new Price(costs, 2));
        }
        return out;
    }

    @Override
    void write(Entity npc, List<List<ItemStack>> converted) {
        MerchantOffers old = offers(npc);
        if (old == null) return;
        MerchantOffers next = new MerchantOffers();
        for (int i = 0; i < old.size(); i++) {
            MerchantOffer o = old.get(i);
            List<ItemStack> c = i < converted.size() ? converted.get(i) : null;
            if (c == null || c.isEmpty()) {
                next.add(o);
                continue;
            }
            ItemStack a = c.get(0);
            Optional<ItemCost> b = c.size() > 1 ? Optional.of(new ItemCost(c.get(1).getItem(), c.get(1).getCount())) : Optional.empty();
            next.add(new MerchantOffer(new ItemCost(a.getItem(), a.getCount()), b, o.getResult().copy(),
                    o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier()));
        }
        setOffers(npc, next);
    }
}
