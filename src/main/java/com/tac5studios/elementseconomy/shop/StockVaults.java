package com.tac5studios.elementseconomy.shop;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stock vaults: bulk storage (Create item vaults) that holds a shop owner's overflow and refills
 * their linked shops. Saved in collection stock_links, key "dimension|position" of the vault
 * (the controller block for multiblocks), value { owner }.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class StockVaults {

    private static final Map<String, UUID> LINKS = new ConcurrentHashMap<>();
    private static int ticks;

    private StockVaults() {}

    static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.asLong();
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        LINKS.clear();
        if (!Storage.running()) return;
        for (String k : Storage.get().keys(Collections.STOCK_LINKS)) {
            JsonObject o = Storage.get().get(Collections.STOCK_LINKS, k, JsonObject.class);
            if (o != null && o.has("owner")) LINKS.put(k, UUID.fromString(o.get("owner").getAsString()));
        }
        // Same as shops: anything that cached a vault's plain handler before the links loaded asks again.
        for (String k : LINKS.keySet()) {
            int bar = k.lastIndexOf('|');
            ResourceLocation dim = bar > 0 ? ResourceLocation.tryParse(k.substring(0, bar)) : null;
            if (dim == null) continue;
            ServerLevel level = e.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            try {
                if (level != null) level.invalidateCapabilities(BlockPos.of(Long.parseLong(k.substring(bar + 1))));
            } catch (NumberFormatException ignored) {
                // bad key, skip
            }
        }
    }

    public static boolean isLinked(Level level, BlockPos controller) {
        return Features.on(Features.STOCK_VAULTS) && LINKS.containsKey(key(level.dimension(), controller));
    }

    @Nullable
    public static UUID owner(Level level, BlockPos controller) {
        return LINKS.get(key(level.dimension(), controller));
    }

    /** Register a vault for an owner. Returns false when someone else owns it. */
    public static boolean link(Level level, BlockPos controller, UUID owner) {
        String k = key(level.dimension(), controller);
        UUID was = LINKS.get(k);
        if (was != null && !was.equals(owner)) return false;
        LINKS.put(k, owner);
        JsonObject o = new JsonObject();
        o.addProperty("owner", owner.toString());
        Storage.get().put(Collections.STOCK_LINKS, k, o);
        level.invalidateCapabilities(controller);
        return true;
    }

    /** Remove a vault and unlink every shop it fed. */
    public static void unlink(Level level, BlockPos controller) {
        String k = key(level.dimension(), controller);
        LINKS.remove(k);
        Storage.get().remove(Collections.STOCK_LINKS, k);
        for (Shop s : Shops.linkedTo(level.dimension(), controller)) {
            s.vault = null;
            Shops.save(s);
        }
        level.invalidateCapabilities(controller);
    }

    // ---------- restocking ----------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (!Features.on(Features.STOCK_VAULTS, Features.SV_AUTO_RESTOCK)) return;
        if (++ticks < ShopConfig.RESTOCK_SECONDS.get() * 20) return;
        ticks = 0;
        MinecraftServer server = e.getServer();
        for (Shop s : Shops.all()) {
            if (s.vault != null) restock(server, s);
        }
    }

    /**
     * Keep a shop and its vault in step. Both must be loaded.
     * BUY shops: refill low items from the vault. SELL shops: move what the shop bought into the vault.
     */
    public static void restock(MinecraftServer server, Shop s) {
        if (s.vault == null || !Features.on(Features.STOCK_VAULTS)) return;
        ServerLevel level = server.getLevel(s.dimension);
        if (level == null || !level.isLoaded(s.pos()) || !level.isLoaded(s.vault)) return;
        if (s.type == ShopType.SELL) {
            for (ShopRow r : s.rows.values()) {
                if (r.item != null) ShopStock.moveShopToVault(level, s, r);
            }
            return;
        }
        int below = ShopConfig.RESTOCK_BELOW.get();
        for (ShopRow r : s.rows.values()) {
            if (!r.listed()) continue;
            int inShop = ShopStock.countShop(level, s, r);
            if (inShop >= below) continue;
            ShopStock.moveVaultToShop(level, s, r, Integer.MAX_VALUE);
        }
    }

    static ResourceKey<Level> dim(String location) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(location));
    }
}
