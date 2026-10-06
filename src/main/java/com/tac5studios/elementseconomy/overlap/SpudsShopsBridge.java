package com.tac5studios.elementseconomy.overlap;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spud's Shops (spudaciousshops), both the spudacious5705 and lucab builds: each shop block entity keeps a
 * ShopInventory (field shopInventory, a NonNullList) whose slot 76 is the price: one item stack, its count
 * is the price. Coin prices follow coin-to-coin switches; the price stays one stack.
 */
final class SpudsShopsBridge extends FoundShopBridge<BlockEntity> {

    static final String MOD = "spudaciousshops";
    static final SpudsShopsBridge INSTANCE = new SpudsShopsBridge();

    private static final int PAYMENT_SLOT = 76;
    private static final Map<Class<?>, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Field NONE;

    static {
        try {
            NONE = SpudsShopsBridge.class.getDeclaredField("INSTANCE");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private SpudsShopsBridge() {
        super("spuds_shops", MOD);
    }

    /** True for a Spud's Shops shop block entity. */
    static boolean isShop(BlockEntity be) {
        ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        return type != null && MOD.equals(type.getNamespace()) && field(be.getClass()) != null;
    }

    private static Field field(Class<?> type) {
        Field f = FIELDS.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null && c != BlockEntity.class; c = c.getSuperclass()) {
                try {
                    Field found = c.getDeclaredField("shopInventory");
                    found.setAccessible(true);
                    return found;
                } catch (NoSuchFieldException ignored) {
                    // look in the parent class
                } catch (RuntimeException ex) {
                    return NONE;
                }
            }
            return NONE;
        });
        return f == NONE ? null : f;
    }

    @SuppressWarnings("unchecked")
    private static NonNullList<ItemStack> inventory(BlockEntity be) {
        try {
            Field f = field(be.getClass());
            Object v = f == null ? null : f.get(be);
            return v instanceof NonNullList<?> list && list.size() > PAYMENT_SLOT ? (NonNullList<ItemStack>) list : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    @Override
    CompoundTag data(BlockEntity shop) {
        return shop.getPersistentData();
    }

    @Override
    void changed(BlockEntity shop) {
        shop.setChanged();
        if (shop.getLevel() != null) {
            BlockState state = shop.getBlockState();
            shop.getLevel().sendBlockUpdated(shop.getBlockPos(), state, state, 3);
        }
    }

    @Override
    List<Price> prices(BlockEntity shop) {
        NonNullList<ItemStack> inv = inventory(shop);
        if (inv == null) return List.of();
        return List.of(new Price(List.of(inv.get(PAYMENT_SLOT)), 1));
    }

    @Override
    void write(BlockEntity shop, List<List<ItemStack>> converted) {
        NonNullList<ItemStack> inv = inventory(shop);
        if (inv == null || converted.isEmpty() || converted.get(0) == null || converted.get(0).isEmpty()) return;
        inv.set(PAYMENT_SLOT, converted.get(0).get(0));
    }
}
