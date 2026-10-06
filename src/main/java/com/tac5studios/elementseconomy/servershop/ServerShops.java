package com.tac5studios.elementseconomy.servershop;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.ItemData;
import com.tac5studios.elementseconomy.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Every server shop, saved in collection server_shops (key = shop id), plus the link index. */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ServerShops {

    private static final Map<String, ServerShop> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> BY_LINK = new ConcurrentHashMap<>(); // link target -> shop id
    private static int ticks;
    private static net.minecraft.core.HolderLookup.Provider registries;

    private ServerShops() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        BY_ID.clear();
        BY_LINK.clear();
        registries = e.getServer().registryAccess();
        if (!Storage.running()) return;
        for (String id : Storage.get().keys(Collections.SERVER_SHOPS)) {
            JsonObject o = Storage.get().get(Collections.SERVER_SHOPS, id, JsonObject.class);
            if (o == null) continue;
            try {
                ServerShop s = ServerShop.fromJson(id, o);
                for (ServerRow r : s.rows.values()) r.item = ItemData.read(r.itemJson, registries);
                index(s);
            } catch (RuntimeException ex) {
                ElementsEconomy.LOGGER.error("[Economy] Server shop {} could not be loaded. Its data is kept.", id, ex);
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        BY_ID.clear();
        BY_LINK.clear();
    }

    /** Stock refills (stock limits on). Checked once a minute. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++ticks < 20 * 60) return;
        ticks = 0;
        if (!Features.on(Features.SERVER_SHOPS, Features.SS_STOCK_LIMITS)) return;
        long now = System.currentTimeMillis();
        for (ServerShop s : BY_ID.values()) {
            boolean changed = false;
            for (ServerRow r : s.rows.values()) {
                if (!r.limited() || r.refillMinutes <= 0 || r.refillAmount <= 0 || r.stock >= r.stockMax) continue;
                long every = r.refillMinutes * 60_000L;
                if (now - r.lastRefill < every) continue;
                long times = r.lastRefill == 0 ? 1 : (now - r.lastRefill) / every;
                r.stock = (int) Math.min(r.stockMax, r.stock + times * r.refillAmount);
                r.lastRefill = now;
                changed = true;
            }
            if (changed) save(s);
        }
    }

    // ---------- lookups ----------

    public static String idOf(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    @Nullable
    public static ServerShop get(String nameOrId) {
        return BY_ID.get(idOf(nameOrId));
    }

    public static Collection<ServerShop> all() {
        return BY_ID.values();
    }

    public static List<String> ids() {
        return new ArrayList<>(BY_ID.keySet());
    }

    public static String blockKey(ResourceKey<Level> dim, BlockPos pos) {
        return "block|" + dim.location() + "|" + pos.asLong();
    }

    public static String entityKey(UUID id) {
        return "entity|" + id;
    }

    @Nullable
    public static ServerShop byLink(String key) {
        String id = BY_LINK.get(key);
        return id == null ? null : BY_ID.get(id);
    }

    // ---------- changes ----------

    private static void index(ServerShop s) {
        BY_ID.put(s.id, s);
        for (ServerShop.Link l : s.links) BY_LINK.put(l.target(), s.id);
    }

    public static void add(ServerShop s) {
        index(s);
        save(s);
    }

    public static void delete(ServerShop s) {
        BY_ID.remove(s.id);
        for (ServerShop.Link l : s.links) BY_LINK.remove(l.target());
        Storage.get().remove(Collections.SERVER_SHOPS, s.id);
    }

    public static void link(ServerShop s, ServerShop.Link link) {
        ServerShop old = byLink(link.target());
        if (old != null) unlink(old, link.target());
        s.links.add(link);
        BY_LINK.put(link.target(), s.id);
        save(s);
    }

    public static boolean unlink(ServerShop s, String target) {
        boolean removed = s.links.removeIf(l -> l.target().equals(target));
        BY_LINK.remove(target);
        if (removed) save(s);
        return removed;
    }

    public static void save(ServerShop s) {
        for (ServerRow r : s.rows.values()) {
            if (r.item != null && registries != null) r.itemJson = ItemData.write(r.item, registries);
        }
        Storage.get().put(Collections.SERVER_SHOPS, s.id, s.toJson());
    }
}
