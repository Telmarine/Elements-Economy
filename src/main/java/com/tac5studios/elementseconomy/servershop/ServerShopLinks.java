package com.tac5studios.elementseconomy.servershop;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Opening server shops from the world: a linked NPC (Easy NPC or any other NPC mod), a staff-made
 * villager, or a linked block. Staff with economy.servershop.edit can sneak-click to open the editor.
 * Runs before other mods and even when a claim mod blocked the click: players only get the shop
 * screen, the NPC or block itself is never used.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ServerShopLinks {

    private ServerShopLinks() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Features.on(Features.SERVER_SHOPS)) return;
        ServerShop s = ServerShops.byLink(ServerShops.entityKey(e.getTarget().getUUID()));
        if (s == null || !allowed(s, e.getTarget().getTags().contains(ServerShopCommands.VILLAGER_TAG)
                ? ServerShop.Link.VILLAGER : ServerShop.Link.NPC)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND) click(p, s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Features.on(Features.SERVER_SHOPS, Features.SS_BLOCK)) return;
        ServerShop s = ServerShops.byLink(ServerShops.blockKey(e.getLevel().dimension(), e.getPos()));
        if (s == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND) click(p, s);
    }

    /** Linked blocks can only be broken by staff (which also unlinks them). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        String key = ServerShops.blockKey(level.dimension(), e.getPos());
        ServerShop s = ServerShops.byLink(key);
        if (s == null) return;
        if (!Perm.has(p, Perm.SERVER_SHOP_EDIT)) {
            e.setCanceled(true);
            return;
        }
        ServerShops.unlink(s, key);
        Msg.send(p, "servershop.unlinked", "name", s.name);
    }

    private static boolean allowed(ServerShop s, String kind) {
        return switch (kind) {
            case ServerShop.Link.VILLAGER -> Features.on(Features.SS_VILLAGER);
            case ServerShop.Link.NPC -> Features.on(Features.SS_NPC_HOOK);
            default -> Features.on(Features.SS_BLOCK);
        };
    }

    private static void click(ServerPlayer p, ServerShop s) {
        if (p.isSecondaryUseActive() && Perm.has(p, Perm.SERVER_SHOP_EDIT)) {
            new ServerEditMenu(s).open(p);
            return;
        }
        openFor(p, s);
    }

    /** The customer screen, if the player may use server shops and the shop is open. */
    public static void openFor(ServerPlayer p, ServerShop s) {
        if (!Perm.has(p, Perm.SERVER_SHOP_USE)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        if (!s.open) {
            Msg.send(p, "shop.closed", "shop", s.name);
            return;
        }
        new ServerTradeMenu(s).open(p);
    }
}
