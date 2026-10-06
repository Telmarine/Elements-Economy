package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Which blocks can be shops or stock vaults, and raw access to their stock.
 * Rule: chests, barrels and storage of the same nature. Never: ender chests, Lootr, network
 * terminals, machines, furniture, safes, sacks (tag elements_economy:not_shop_containers).
 */
public final class ShopContainers {

    public static final TagKey<Block> C_CHESTS = tag("c", "chests");
    public static final TagKey<Block> C_BARRELS = tag("c", "barrels");
    public static final TagKey<Block> C_ENDER = tag("c", "chests/ender");
    public static final TagKey<Block> ALLOW = tag("elements_economy", "shop_containers");
    public static final TagKey<Block> DENY = tag("elements_economy", "not_shop_containers");
    public static final TagKey<Block> STOCK_VAULTS = tag("elements_economy", "stock_vaults");

    /** Set while Economy itself moves stock, so the shop lock lets it through. */
    static final ThreadLocal<Boolean> RAW = ThreadLocal.withInitial(() -> false);

    private ShopContainers() {}

    private static TagKey<Block> tag(String ns, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ns, path));
    }

    /** True when this block may become a player shop. */
    public static boolean canBeShop(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block b = state.getBlock();
        if (state.is(DENY) || state.is(C_ENDER) || b instanceof EnderChestBlock || state.is(STOCK_VAULTS)) return false;
        if (level.getBlockEntity(pos) == null) return false;
        if (Features.on(Features.CONTAINERS_CHESTS) && b instanceof ChestBlock) return true;
        if (Features.on(Features.CONTAINERS_BARRELS) && b instanceof BarrelBlock) return true;
        if (Features.on(Features.CONTAINERS_SHULKERS) && b instanceof ShulkerBoxBlock) return true;
        if (Features.on(Features.CONTAINERS_COMMON_TAGS) && (state.is(C_CHESTS) || state.is(C_BARRELS))) return true;
        if (Features.on(Features.CONTAINERS_ALLOW_TAG) && state.is(ALLOW)) return true;
        return Features.on(Features.CONTAINERS_ANY) && raw(level, pos) != null;
    }

    public static boolean isStockVault(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(STOCK_VAULTS) && raw(level, pos) != null;
    }

    /** The other half of a double chest, or null. */
    @Nullable
    public static BlockPos otherHalf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE)) return null;
        if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    /**
     * The real inventory, past the shop lock. Uses the block's own item handler, or wraps the
     * container when the block has none (BCLib chests). Double chests give both halves.
     */
    @Nullable
    public static IItemHandler raw(Level level, BlockPos pos) {
        boolean was = RAW.get();
        RAW.set(true);
        try {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null);
            if (h != null) return h;
            if (state.getBlock() instanceof ChestBlock chest) {
                Container c = ChestBlock.getContainer(chest, state, level, pos, true);
                if (c != null) return new InvWrapper(c);
            }
            if (be instanceof Container c) return new InvWrapper(c);
            return null;
        } finally {
            RAW.set(was);
        }
    }
}
