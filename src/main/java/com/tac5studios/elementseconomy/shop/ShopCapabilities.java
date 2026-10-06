package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.adapters.Reflect;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop lock. Registered first for every block that has a block entity, so it answers before the
 * block's own handler. For shop containers and linked stock vaults it hands out a guarded handler:
 * items can go in (hoppers, belts, farms), nothing comes out except through Economy.
 * For every other block it answers nothing and the block's own handler is used as normal.
 * Vanilla hoppers ask this capability first too (NeoForge's hopper hook).
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER, bus = EventBusSubscriber.Bus.MOD)
public final class ShopCapabilities {

    private ShopCapabilities() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void register(RegisterCapabilitiesEvent e) {
        List<Block> blocks = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (b instanceof EntityBlock) blocks.add(b);
        }
        e.registerBlock(Capabilities.ItemHandler.BLOCK, ShopCapabilities::provide, blocks.toArray(new Block[0]));
        ElementsEconomy.LOGGER.info("[Economy] Shop lock ready for {} storage blocks.", blocks.size());
    }

    @Nullable
    private static IItemHandler provide(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
        if (ShopContainers.RAW.get() || level.isClientSide()) return null;
        boolean shop = Features.on(Features.PLAYER_SHOPS, Features.PS_BLOCK_EXTRACTION) && Shops.isShop(level, pos);
        boolean vault = !shop && state.is(ShopContainers.STOCK_VAULTS) && StockVaults.isLinked(level, controller(be, pos));
        if (!shop && !vault) return null;
        IItemHandler inner = ShopContainers.raw(level, pos);
        return inner == null ? null : new Guarded(inner);
    }

    /** The controller block of a multiblock (Create vaults), or the block itself. */
    static BlockPos controller(@Nullable BlockEntity be, BlockPos pos) {
        if (be == null) return pos;
        try {
            if (Reflect.hasMethod(be.getClass().getName(), "getController", 0)) {
                Object c = Reflect.call(be, "getController");
                if (c instanceof BlockPos p) return p;
            }
        } catch (RuntimeException ignored) {
            // not a multiblock
        }
        return pos;
    }

    /** Insert allowed, extract blocked. */
    private record Guarded(IItemHandler inner) implements IItemHandler {
        public int getSlots() {
            return inner.getSlots();
        }

        public @NotNull ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return inner.insertItem(slot, stack, simulate);
        }

        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return inner.isItemValid(slot, stack);
        }
    }
}
