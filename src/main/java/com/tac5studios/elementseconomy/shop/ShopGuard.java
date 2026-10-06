package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shop container protection:
 *  - Right-click: customers get the shop screen; the owner gets the owner screen. Claim mods decide
 *    first, unless the owner switched on claim access (then customers go straight to the shop screen)
 *    (sneak with an empty hand opens the container itself). Staff with economy.shop.bypass can sneak to open it.
 *  - Breaking: only the owner (removes the shop) and staff. Linked stock vaults likewise.
 *  - Explosions skip shops and linked vaults.
 *  - Nobody but the owner can join a chest onto a shop chest to reach its stock.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ShopGuard {

    private ShopGuard() {}

    /**
     * Called by the mixin at the very start of a right-click on a block, before any mod's click event.
     * Only for customers of a shop whose owner switched on claim access: they get the shop screen and the
     * click ends there. The container is never touched, so no claim mod needs to step in (and none warns).
     * Returns true when the click was handled.
     */
    public static boolean earlyClick(ServerPlayer p, BlockPos pos, InteractionHand hand) {
        if (p.isSpectator() || !Features.on(Features.PLAYER_SHOPS, Features.PS_CLAIM_ACCESS)) return false;
        Shop s = Shops.at(p.level(), pos);
        if (s == null || !s.claimAccess || s.owner.equals(p.getUUID())) return false;
        if (p.isSecondaryUseActive() && (Perm.has(p, Perm.SHOP_BYPASS) || !Features.on(Features.PS_PROTECTION))) return false;
        if (hand == InteractionHand.MAIN_HAND) openFor(p, s);
        return true;
    }

    /**
     * Every other shop click. Runs after claim mods (OPAC, FTB Chunks): when a claim blocks a customer,
     * the claim's rules stand. The owner can switch on claim access to let customers through instead.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Features.on(Features.PLAYER_SHOPS)) return;
        Shop s = Shops.at(e.getLevel(), e.getPos());
        if (s == null) return;

        boolean owner = s.owner.equals(p.getUUID());
        boolean sneaking = p.isSecondaryUseActive();
        if (sneaking && (owner || Perm.has(p, Perm.SHOP_BYPASS) || !Features.on(Features.PS_PROTECTION))) {
            return;                                                            // the container itself opens
        }

        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;                  // open once, not per hand

        if (owner && Features.on(Features.PS_MANAGE) && Perm.has(p, Perm.SHOP_MANAGE)) {
            new OwnerMenu(s).open(p);
        } else {
            openFor(p, s);
        }
    }

    /** Customer screen, or a "closed" note when the owner has closed the shop. */
    private static void openFor(ServerPlayer p, Shop s) {
        if (!s.open) {
            Msg.send(p, "shop.closed", "shop", s.name);
            return;
        }
        new TradeMenu(s).open(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        BlockPos pos = e.getPos();

        Shop s = Shops.at(level, pos);
        if (s != null) {
            boolean owner = s.owner.equals(p.getUUID());
            boolean staff = Perm.has(p, Perm.SHOP_BYPASS);
            if (!owner && !staff && Features.on(Features.PS_PROTECTION)) {
                e.setCanceled(true);
                Msg.send(p, "shop.protected", "owner", s.ownerName);
                return;
            }
            // Owner or staff breaks it: the shop goes, but only if its till can be paid out.
            if (!ShopCommands.remove(p, s)) e.setCanceled(true);
            return;
        }

        if (level.getBlockState(pos).is(ShopContainers.STOCK_VAULTS)) {
            BlockPos controller = ShopCapabilities.controller(level.getBlockEntity(pos), pos);
            UUID owner = StockVaults.owner(level, controller);
            if (owner == null) return;
            if (!owner.equals(p.getUUID()) && !Perm.has(p, Perm.SHOP_BYPASS)) {
                e.setCanceled(true);
                Msg.send(p, "shop.protected", "owner", "?");
                return;
            }
            StockVaults.unlink(level, controller);
        }
    }

    @SubscribeEvent
    public static void onExplode(ExplosionEvent.Detonate e) {
        if (!Features.on(Features.PLAYER_SHOPS, Features.PS_EXPLOSION_BLOCK)) return;
        Level level = e.getLevel();
        e.getAffectedBlocks().removeIf(pos -> Shops.isShop(level, pos) || linkedVault(level, pos));
    }

    private static boolean linkedVault(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.is(ShopContainers.STOCK_VAULTS)
                && StockVaults.isLinked(level, ShopCapabilities.controller(level.getBlockEntity(pos), pos));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level level) || !(e.getPlacedBlock().getBlock() instanceof ChestBlock)) return;
        List<Shop> next = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Shop s = Shops.at(level, e.getPos().relative(d));
            if (s != null && level.getBlockState(s.pos()).getBlock() instanceof ChestBlock) next.add(s);
        }
        if (next.isEmpty()) return;
        UUID placer = e.getEntity() == null ? null : e.getEntity().getUUID();
        for (Shop s : next) {
            if (placer == null || !s.owner.equals(placer)) {
                e.setCanceled(true);
                if (e.getEntity() instanceof ServerPlayer p) Msg.send(p, "shop.protected", "owner", s.ownerName);
                return;
            }
        }
        // The owner extended their shop chest: index the new half next tick, once the chests have joined.
        if (level.getServer() == null) return;
        level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
            for (Shop s : next) {
                List<BlockPos> positions = new ArrayList<>();
                positions.add(s.pos());
                BlockPos other = ShopContainers.otherHalf(level, s.pos());
                if (other != null) positions.add(other);
                Shops.reindex(s, positions);
                for (BlockPos bp : positions) level.invalidateCapabilities(bp);
            }
        }));
    }
}
