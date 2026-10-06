package com.tac5studios.elementseconomy.overlap;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the found-shop bridges' lists of loaded shops, and converts a shop that missed a currency switch
 * when it loads (bridge.convert_on_load) or right before someone uses it.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class FoundShopEvents {

    private FoundShopEvents() {}

    private static boolean onLoad() {
        return Features.on(Features.SHOP_BRIDGE, Features.BRIDGE_CONVERT_ON_LOAD);
    }

    // ---------- Spud's Shops ----------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getChunk() instanceof LevelChunk chunk)) return;
        if (!SpudsShopsBridge.INSTANCE.isAvailable()) return;
        List<BlockEntity> shops = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) if (SpudsShopsBridge.isShop(be)) shops.add(be);
        if (shops.isEmpty()) return;
        level.getServer().execute(() -> {
            for (BlockEntity be : shops) {
                if (be.isRemoved()) continue;
                SpudsShopsBridge.INSTANCE.track(be);
                if (onLoad()) SpudsShopsBridge.INSTANCE.catchUp(be);
            }
        });
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel) || !(e.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity be : chunk.getBlockEntities().values()) SpudsShopsBridge.INSTANCE.untrack(be);
    }

    /** Before a Spud's shop is used: also catches shops placed since their chunk loaded. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !SpudsShopsBridge.INSTANCE.isAvailable()) return;
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (be == null || !SpudsShopsBridge.isShop(be)) return;
        SpudsShopsBridge.INSTANCE.track(be);
        SpudsShopsBridge.INSTANCE.catchUp(be);
    }

    // ---------- Easy NPC ----------

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !EasyNpcBridge.isNpc(e.getEntity())) return;
        if (!EasyNpcBridge.INSTANCE.isAvailable()) return;
        Entity npc = e.getEntity();
        level.getServer().execute(() -> {
            if (npc.isRemoved()) return;
            EasyNpcBridge.INSTANCE.track(npc);
            if (onLoad()) EasyNpcBridge.INSTANCE.catchUp(npc);
        });
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide()) EasyNpcBridge.INSTANCE.untrack(e.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onUseEntity(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || !EasyNpcBridge.isNpc(e.getTarget()) || !EasyNpcBridge.INSTANCE.isAvailable()) return;
        EasyNpcBridge.INSTANCE.catchUp(e.getTarget());
    }
}
