package com.tac5studios.elementseconomy.bridge;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementsvault.event.RegisterShopBridgesEvent;
import com.tac5studios.elementsvault.shop.ShopBridge;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Other mods' shops and auction houses connected through Elements: Vault ({@link ShopBridge}).
 * Collected once at server start, before shops load. The currency switch-over hands each one the change.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class ShopBridges {

    private static final Map<ResourceLocation, ShopBridge> BRIDGES = new LinkedHashMap<>();

    private ShopBridges() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        BRIDGES.clear();
        if (!Features.on(Features.SHOP_BRIDGE)) return;
        // Shop mods found by the overlap guard (Spud's Shops, Easy NPC).
        for (ShopBridge b : com.tac5studios.elementseconomy.overlap.OverlapGuard.foundShopBridges()) add(b);
        // Other mods' own bridges.
        if (Features.on(Features.API, Features.API_EXTERNAL_BRIDGES)) {
            NeoForge.EVENT_BUS.post(new RegisterShopBridgesEvent(ShopBridges::add));
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        BRIDGES.clear();
    }

    private static void add(ShopBridge b) {
        try {
            if (b == null || !b.isAvailable() || BRIDGES.containsKey(b.id())) return;
            BRIDGES.put(b.id(), b);
            ElementsEconomy.LOGGER.info("[Economy] Shop bridge added: {} ({})", b.id(), b.modId());
        } catch (RuntimeException ex) {
            ElementsEconomy.LOGGER.warn("[Economy] A shop bridge could not be added: {}", ex.toString());
        }
    }

    public static List<ShopBridge> all() {
        return new ArrayList<>(BRIDGES.values());
    }
}
