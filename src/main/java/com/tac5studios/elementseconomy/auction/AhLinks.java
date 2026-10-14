package com.tac5studios.elementseconomy.auction;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.servershop.ServerShops;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opening the auction house from the world: an NPC from any NPC mod, a villager made by staff, or a
 * block. Saved in collection ah_links (key = link target, same keys as server shop links).
 * Staff link them with /ah link npc|villager|block and remove them with /ah unlink.
 * A target linked to a server shop always opens the server shop.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class AhLinks {

    public static final String NPC = "npc";
    public static final String VILLAGER = "villager";
    public static final String BLOCK = "block";

    /** Tag on villagers made for the auction house. */
    public static final String VILLAGER_TAG = "elements_economy_ah_villager";

    private static final Map<String, String> LINKS = new ConcurrentHashMap<>(); // target -> kind

    private AhLinks() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        LINKS.clear();
        if (!Storage.running()) return;
        for (String key : Storage.get().keys(Collections.AH_LINKS)) {
            JsonObject o = Storage.get().get(Collections.AH_LINKS, key, JsonObject.class);
            if (o != null && o.has("kind")) LINKS.put(key, o.get("kind").getAsString());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        LINKS.clear();
    }

    // ---------- links ----------

    public static boolean linked(String target) {
        return LINKS.containsKey(target);
    }

    public static void link(String target, String kind) {
        LINKS.put(target, kind);
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        Storage.get().put(Collections.AH_LINKS, target, o);
    }

    public static boolean unlink(String target) {
        if (LINKS.remove(target) == null) return false;
        Storage.get().remove(Collections.AH_LINKS, target);
        return true;
    }

    private static boolean blocked() {
        return !Features.on(Features.AUCTION, Features.AH_LINKS);
    }

    // ---------- clicks ----------

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        String key = ServerShops.entityKey(e.getTarget().getUUID());
        String kind = LINKS.get(key);
        if (kind == null || ServerShops.byLink(key) != null || blocked()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND) open(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        String key = ServerShops.blockKey(e.getLevel().dimension(), e.getPos());
        String kind = LINKS.get(key);
        if (kind == null || ServerShops.byLink(key) != null || blocked()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND) open(p);
    }

    /** Linked blocks can only be broken by staff who can link (which also unlinks them). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level level) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        String key = ServerShops.blockKey(level.dimension(), e.getPos());
        if (!LINKS.containsKey(key)) return;
        if (!Perm.has(p, Perm.AH_LINK)) {
            e.setCanceled(true);
            return;
        }
        unlink(key);
        Msg.send(p, "ah.unlinked");
    }

    private static void open(ServerPlayer p) {
        if (!Perm.has(p, Perm.AH_OPEN)) {
            Msg.send(p, "general.no_permission");
            return;
        }
        new AhBrowseMenu().open(p);
    }
}
