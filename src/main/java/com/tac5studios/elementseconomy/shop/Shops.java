package com.tac5studios.elementseconomy.shop;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.ItemData;
import com.tac5studios.elementseconomy.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Every player shop, kept in memory and saved to the data store (collection player_shops). */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Shops {

    private static final Map<String, Shop> BY_ID = new ConcurrentHashMap<>();
    /** dimension -> block position -> shop id (both halves of a double chest). */
    private static final Map<ResourceKey<Level>, Map<Long, String>> BY_POS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static HolderLookup.Provider registries;

    private Shops() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        BY_ID.clear();
        BY_POS.clear();
        registries = e.getServer().registryAccess();
        if (!Storage.running()) return;
        int n = 0;
        for (String id : Storage.get().keys(Collections.PLAYER_SHOPS)) {
            JsonObject o = Storage.get().get(Collections.PLAYER_SHOPS, id, JsonObject.class);
            if (o == null) continue;
            try {
                Shop s = Shop.fromJson(id, o);
                for (ShopRow r : s.rows.values()) r.item = ItemData.read(r.itemJson, registries);
                index(s);
                n++;
            } catch (RuntimeException ex) {
                ElementsEconomy.LOGGER.error("[Economy] Shop {} could not be loaded. Its data is kept.", id, ex);
            }
        }
        if (n > 0) ElementsEconomy.LOGGER.info("[Economy] {} player shops loaded.", n);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        BY_ID.clear();
        BY_POS.clear();
    }

    public static HolderLookup.Provider registries() {
        return registries;
    }

    private static void index(Shop s) {
        BY_ID.put(s.id, s);
        Map<Long, String> m = BY_POS.computeIfAbsent(s.dimension, k -> new ConcurrentHashMap<>());
        for (BlockPos p : s.positions) m.put(p.asLong(), s.id);
    }

    @Nullable
    public static Shop at(ResourceKey<Level> dim, BlockPos pos) {
        Map<Long, String> m = BY_POS.get(dim);
        if (m == null) return null;
        String id = m.get(pos.asLong());
        return id == null ? null : BY_ID.get(id);
    }

    @Nullable
    public static Shop at(Level level, BlockPos pos) {
        return at(level.dimension(), pos);
    }

    public static boolean isShop(Level level, BlockPos pos) {
        Map<Long, String> m = BY_POS.get(level.dimension());
        return m != null && m.containsKey(pos.asLong());
    }

    @Nullable
    public static Shop get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Shop> all() {
        return BY_ID.values();
    }

    public static List<Shop> ownedBy(UUID owner) {
        List<Shop> out = new ArrayList<>();
        for (Shop s : BY_ID.values()) if (s.owner.equals(owner)) out.add(s);
        return out;
    }

    /** A new short id (8 base-36 characters). */
    public static String newId() {
        String id;
        do {
            id = Long.toString(Math.abs(RANDOM.nextLong()), 36);
            id = id.length() > 8 ? id.substring(0, 8) : id;
        } while (BY_ID.containsKey(id));
        return id;
    }

    public static void add(Shop s) {
        index(s);
        save(s);
    }

    public static void remove(Shop s) {
        BY_ID.remove(s.id);
        ShopDisplays.remove(s.id);
        Map<Long, String> m = BY_POS.get(s.dimension);
        if (m != null) for (BlockPos p : s.positions) m.remove(p.asLong());
        Storage.get().remove(Collections.PLAYER_SHOPS, s.id);
        Storage.get().remove(Collections.DAILY, s.id);
        Storage.moneyChanged();
    }

    /** Update the position index after a shop's blocks changed (a chest was joined into a double chest). */
    public static void reindex(Shop s, List<BlockPos> newPositions) {
        Map<Long, String> m = BY_POS.computeIfAbsent(s.dimension, k -> new ConcurrentHashMap<>());
        for (BlockPos p : s.positions) m.remove(p.asLong());
        s.positions.clear();
        s.positions.addAll(newPositions);
        index(s);
        save(s);
    }

    /** Save after any change. Rows keep their original item data. */
    public static void save(Shop s) {
        for (ShopRow r : s.rows.values()) {
            if (r.item != null && registries != null) r.itemJson = ItemData.write(r.item, registries);
        }
        Storage.get().put(Collections.PLAYER_SHOPS, s.id, s.toJson());
        Storage.moneyChanged();
    }

    /** Shops linked to a vault. */
    public static List<Shop> linkedTo(ResourceKey<Level> dim, BlockPos vault) {
        List<Shop> out = new ArrayList<>();
        for (Shop s : BY_ID.values()) if (s.dimension.equals(dim) && vault.equals(s.vault)) out.add(s);
        return out;
    }

    /** Count of shops per owner, for limits. */
    public static Map<UUID, Integer> counts() {
        Map<UUID, Integer> m = new HashMap<>();
        for (Shop s : BY_ID.values()) m.merge(s.owner, 1, Integer::sum);
        return m;
    }
}
