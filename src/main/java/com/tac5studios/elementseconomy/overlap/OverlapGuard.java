package com.tac5studios.elementseconomy.overlap;

import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.Features;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Finds other mods that do the job of a built-in feature and turns the built-in one off, so players never
 * get two shop systems or two /bal commands. features.toml is not changed: the feature is only off while
 * the other mod is installed. overlap_guard.force_on keeps a built-in feature on anyway.
 *
 * Decided once from the installed mod list (the guard is a restart switch); {@link Features#on} asks here.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class OverlapGuard {

    /** A built-in feature, the name force_on uses, and the mods that replace it. */
    private record Rule(String name, String label, List<BooleanValue> switches, Map<String, String> mods) {}

    private static volatile Map<BooleanValue, String> blocked;
    private static final Map<String, List<String>> FOUND = new LinkedHashMap<>();

    private OverlapGuard() {}

    private static List<Rule> rules() {
        return List.of(
                new Rule("player_shops", "player shops", List.of(Features.PLAYER_SHOPS),
                        Map.of("spudaciousshops", "Spud's Shops")),
                new Rule("server_shops", "server shops", List.of(Features.SERVER_SHOPS),
                        mods("easy_npc", "Easy NPC", "sdmshop", "SDM Shop", "adminshop", "AdminShop")),
                new Rule("auction_house", "auction house", List.of(Features.AUCTION),
                        mods("arcadia_ah", "Arcadia AH", "economycraft", "EconomyCraft", "migueleconomy", "MiguelEconomy")),
                new Rule("economy_commands", "money commands (/bal, /pay, /baltop, /payments, /eco)",
                        List.of(Features.ECO_BALANCE, Features.ECO_BALANCE_OTHERS, Features.ECO_PAY, Features.ECO_PAY_TOGGLE,
                                Features.ECO_BALTOP, Features.ECO_HISTORY, Features.ECO_GIVE, Features.ECO_TAKE,
                                Features.ECO_SET, Features.ECO_RESET),
                        mods("economycraft", "EconomyCraft", "migueleconomy", "MiguelEconomy"))
        );
    }

    private static Map<String, String> mods(String... idAndName) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < idAndName.length; i += 2) m.put(idAndName[i], idAndName[i + 1]);
        return m;
    }

    /** True when the guard turned this switch off. */
    public static boolean blocks(BooleanValue feature) {
        Map<BooleanValue, String> b = blocked;
        if (b == null) b = decide();
        return b.containsKey(feature);
    }

    private static synchronized Map<BooleanValue, String> decide() {
        if (blocked != null) return blocked;
        Map<BooleanValue, String> out = new IdentityHashMap<>();
        FOUND.clear();
        if (Features.OVERLAP_GUARD.get()) {
            ModList list = ModList.get();
            for (Rule r : rules()) {
                List<String> found = new ArrayList<>();
                r.mods().forEach((id, name) -> {
                    if (list.isLoaded(id)) found.add(name);
                });
                if (found.isEmpty()) continue;
                FOUND.put(r.label(), found);
                if (Features.forcedOn(r.name())) continue;
                for (BooleanValue v : r.switches()) out.put(v, r.name());
            }
        }
        blocked = out;
        return out;
    }

    /** Mod names found per built-in feature (for the bridge and the log). */
    public static Map<String, List<String>> found() {
        if (blocked == null) decide();
        return Collections.unmodifiableMap(FOUND);
    }

    /** True when any of these mods is installed and the guard is on (used by the found-shop bridges). */
    public static boolean installed(String modId) {
        return Features.OVERLAP_GUARD.get() && ModList.get().isLoaded(modId);
    }

    /** Bridges for found shop mods whose prices are coin items (switch-over converts them). */
    public static List<com.tac5studios.elementsvault.shop.ShopBridge> foundShopBridges() {
        List<com.tac5studios.elementsvault.shop.ShopBridge> out = new ArrayList<>();
        if (SpudsShopsBridge.INSTANCE.isAvailable()) out.add(SpudsShopsBridge.INSTANCE);
        if (EasyNpcBridge.INSTANCE.isAvailable()) out.add(EasyNpcBridge.INSTANCE);
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        decide();
        if (FOUND.isEmpty()) return;
        Set<String> off = new java.util.HashSet<>(blocked.values());
        for (Rule r : rules()) {
            List<String> mods = FOUND.get(r.label());
            if (mods == null) continue;
            if (off.contains(r.name())) {
                ElementsEconomy.LOGGER.info("[Economy] {} found: built-in {} turned off. Add \"{}\" to overlap_guard.force_on to keep it.",
                        String.join(", ", mods), r.label(), r.name());
            } else {
                ElementsEconomy.LOGGER.info("[Economy] {} found: built-in {} kept on (overlap_guard.force_on).",
                        String.join(", ", mods), r.label());
            }
        }
    }
}
