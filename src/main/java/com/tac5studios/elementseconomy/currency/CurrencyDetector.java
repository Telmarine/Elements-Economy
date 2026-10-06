package com.tac5studios.elementseconomy.currency;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import com.tac5studios.elementseconomy.config.Features;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All-in-one currency detection. One switch (currency.auto_detect) checks every mod in {@link KnownCurrencies}.
 * A mod counts as found when its mod ID is loaded and one of its marker classes exists.
 * Item currencies get their values from config/elements_economy/currency_values.toml.
 *
 * Vanilla items are never money on their own. A server owner can add them under [minecraft]
 * in the values file. Each one must then be confirmed by an admin before it counts.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class CurrencyDetector {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String VALUES_FILE = "elements_economy/currency_values.toml";
    private static final String VANILLA = "minecraft";
    private static final List<String> CONFIRMED_KEY = List.of("confirmed", "vanilla");
    private static final List<String> IGNORED_KEY = List.of("confirmed", "ignored");

    private static List<KnownCurrency> found = List.of();
    private static Map<Item, Long> itemValues = Map.of();
    private static Set<ResourceLocation> pendingVanilla = Set.of();
    private static Map<String, List<Map.Entry<Item, Long>>> denominations = Map.of();

    private CurrencyDetector() {}

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        refresh();
    }

    /** Detection at startup and on /economy reload. Does nothing (finds nothing) when auto_detect is off. */
    public static void refresh() {
        if (!Features.on(Features.CURRENCY_AUTO_DETECT)) {
            found = List.of();
            itemValues = Map.of();
            pendingVanilla = Set.of();
            denominations = Map.of();
            return;
        }
        detect();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            askToConfirm(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            askToConfirm(p);
        }
    }

    // ------------------------------------------------------------------ public

    /** Runs detection and loads item values. Safe to call again on reload. */
    public static void detect() {
        List<KnownCurrency> hits = new ArrayList<>();
        for (KnownCurrency c : KnownCurrencies.ALL) {
            if (isPresent(c)) {
                hits.add(c);
            }
        }
        found = Collections.unmodifiableList(hits);
        Set<ResourceLocation> pending = new LinkedHashSet<>();
        itemValues = Collections.unmodifiableMap(loadItemValues(hits, pending));
        pendingVanilla = Collections.unmodifiableSet(pending);
        denominations = buildDenominations(itemValues);

        if (Features.on(Features.CURRENCY_DETECT_LOG)) {
            log();
        }
        for (ResourceLocation id : pendingVanilla) {
            LOG.warn("[Elements: Economy] {} is listed as currency. Vanilla items are not money by default. "
                    + "Confirm with /economy currency confirm {} or remove it with /economy currency ignore {}.", id, id, id);
        }
    }

    /** Every currency mod found at startup. */
    public static List<KnownCurrency> found() {
        return found;
    }

    /** True when a supported currency with this mod ID and kind was found. */
    public static boolean isFound(String modId, CurrencyKind kind) {
        for (KnownCurrency c : found) {
            if (c.modId().equals(modId) && c.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** Vanilla items added to the values file that still wait for an admin to confirm them. */
    public static Set<ResourceLocation> pendingVanilla() {
        return pendingVanilla;
    }

    /** True for any minecraft: item. */
    public static boolean isVanilla(ResourceLocation id) {
        return VANILLA.equals(id.getNamespace());
    }

    /** Value of one item, or 0 when it is not counted as money. */
    public static long valueOf(Item item) {
        Long v = itemValues.get(item);
        return v == null ? 0L : v;
    }

    /** True when this item is counted as money. */
    public static boolean isMoney(Item item) {
        return itemValues.containsKey(item);
    }

    /** Every item-currency namespace that has at least one counted coin (e.g. "aiycoin", or "minecraft" once confirmed). */
    public static Set<String> itemCurrencies() {
        return denominations.keySet();
    }

    /** Coins of one item currency, largest value first. Empty when the currency has no counted items. */
    public static List<Map.Entry<Item, Long>> denominations(String namespace) {
        return denominations.getOrDefault(namespace, List.of());
    }

    /** Admin confirmed a vanilla item as currency. Returns false when it was not waiting. */
    public static boolean confirm(ResourceLocation id) {
        if (!pendingVanilla.contains(id)) {
            return false;
        }
        if (!updateList(CONFIRMED_KEY, id, true) || !updateList(IGNORED_KEY, id, false)) {
            return false;
        }
        detect();
        return true;
    }

    /** Admin said no. The item stays in the file but is never counted. Returns false when it was not waiting. */
    public static boolean ignore(ResourceLocation id) {
        if (!pendingVanilla.contains(id)) {
            return false;
        }
        if (!updateList(IGNORED_KEY, id, true) || !updateList(CONFIRMED_KEY, id, false)) {
            return false;
        }
        detect();
        return true;
    }

    /** Sends the "are you sure" message to one admin, if anything waits. */
    public static void askToConfirm(ServerPlayer player) {
        if (pendingVanilla.isEmpty() || !Perm.has(player, Perm.CURRENCY_CONFIRM)) {
            return;
        }
        for (ResourceLocation id : pendingVanilla) {
            player.sendSystemMessage(confirmMessage(id));
        }
    }

    /** Sends the "are you sure" message to every online admin. */
    public static void askAllAdmins(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            askToConfirm(p);
        }
    }

    // ------------------------------------------------------------------ internals

    private static Component confirmMessage(ResourceLocation id) {
        return Msg.join(
                Msg.text("currency.vanilla_ask", "item", id),
                Msg.button("currency.confirm_button", "currency.confirm_hover", "/economy currency confirm " + id, "item", id),
                Msg.button("currency.ignore_button", "currency.ignore_hover", "/economy currency ignore " + id, "item", id));
    }

    private static boolean isPresent(KnownCurrency c) {
        if (!ModList.get().isLoaded(c.modId())) {
            return false;
        }
        if (c.markers().isEmpty()) {
            return true;
        }
        for (String marker : c.markers()) {
            if (classExists(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, CurrencyDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static Path valuesFile() {
        return FMLPaths.CONFIGDIR.get().resolve(VALUES_FILE);
    }

    /** Adds missing mods and items to the values file, then reads every value back. */
    private static Map<Item, Long> loadItemValues(List<KnownCurrency> hits, Set<ResourceLocation> pending) {
        Map<Item, Long> values = new HashMap<>();
        Path file = valuesFile();
        try {
            Files.createDirectories(file.getParent());
            boolean isNew = Files.notExists(file);

            try (CommentedFileConfig cfg = CommentedFileConfig.builder(file).preserveInsertionOrder().build()) {
                cfg.load();
                boolean changed = isNew;

                // Write defaults for every detected item currency.
                for (KnownCurrency c : hits) {
                    if (!c.hasItems()) {
                        continue;
                    }
                    List<String> section = List.of(c.modId());
                    boolean newSection = !cfg.contains(section);
                    for (Map.Entry<String, Long> e : c.items().entrySet()) {
                        List<String> key = List.of(c.modId(), e.getKey());
                        if (!cfg.contains(key)) {
                            cfg.set(key, e.getValue());
                            changed = true;
                        }
                    }
                    if (newSection) {
                        cfg.setComment(section, " " + c.name() + ". " + c.note() + "\n"
                                + " Each number is how much money one item is worth. 0 = not counted.");
                    }
                }
                if (!cfg.contains(List.of("confirmed"))) {
                    cfg.set(CONFIRMED_KEY, new ArrayList<String>());
                    cfg.set(IGNORED_KEY, new ArrayList<String>());
                    cfg.setComment(List.of("confirmed"),
                            " Vanilla items an admin has confirmed or ignored as currency.\n"
                                    + " To use a vanilla item as money, add it under [minecraft], e.g. emerald = 1.\n"
                                    + " An admin is then asked to confirm it in game.");
                    changed = true;
                }
                if (changed) {
                    cfg.save();
                }

                // Read mod currencies.
                for (KnownCurrency c : hits) {
                    if (!c.hasItems()) {
                        continue;
                    }
                    for (String path : c.items().keySet()) {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(c.modId(), path);
                        if (isVanilla(id)) {
                            continue;
                        }
                        putValue(values, id, cfg.get(List.of(c.modId(), path)));
                    }
                }

                // Read vanilla items the owner added. They count only once confirmed.
                Set<String> confirmed = readList(cfg, CONFIRMED_KEY);
                Set<String> ignored = readList(cfg, IGNORED_KEY);
                Object vanillaSection = cfg.get(List.of(VANILLA));
                if (vanillaSection instanceof com.electronwill.nightconfig.core.UnmodifiableConfig vc) {
                    for (com.electronwill.nightconfig.core.UnmodifiableConfig.Entry e : vc.entrySet()) {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(VANILLA, e.getKey());
                        long value = e.getRawValue() instanceof Number n ? n.longValue() : 0L;
                        if (value <= 0 || ignored.contains(id.toString())) {
                            continue;
                        }
                        if (!BuiltInRegistries.ITEM.containsKey(id)) {
                            LOG.warn("[Elements: Economy] Currency item {} not found. Skipped.", id);
                            continue;
                        }
                        if (confirmed.contains(id.toString())) {
                            values.put(BuiltInRegistries.ITEM.get(id), value);
                        } else {
                            pending.add(id);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("[Elements: Economy] Could not read {}. Item currencies are off until it is fixed.", file, e);
            values.clear();
            pending.clear();
        }
        return values;
    }

    private static Map<String, List<Map.Entry<Item, Long>>> buildDenominations(Map<Item, Long> values) {
        Map<String, List<Map.Entry<Item, Long>>> out = new HashMap<>();
        for (Map.Entry<Item, Long> e : values.entrySet()) {
            String ns = BuiltInRegistries.ITEM.getKey(e.getKey()).getNamespace();
            out.computeIfAbsent(ns, k -> new ArrayList<>()).add(Map.entry(e.getKey(), e.getValue()));
        }
        for (List<Map.Entry<Item, Long>> list : out.values()) {
            list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        }
        Map<String, List<Map.Entry<Item, Long>>> frozen = new HashMap<>();
        out.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(frozen);
    }

    private static void putValue(Map<Item, Long> values, ResourceLocation id, Object raw) {
        long value = raw instanceof Number n ? n.longValue() : 0L;
        if (value <= 0) {
            return;
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            values.put(BuiltInRegistries.ITEM.get(id), value);
        } else {
            LOG.warn("[Elements: Economy] Currency item {} not found. Skipped.", id);
        }
    }

    private static Set<String> readList(CommentedFileConfig cfg, List<String> key) {
        Set<String> out = new LinkedHashSet<>();
        if (cfg.get(key) instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /** Adds or removes one item in a list in the values file. */
    private static boolean updateList(List<String> key, ResourceLocation id, boolean add) {
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(valuesFile()).preserveInsertionOrder().build()) {
            cfg.load();
            Set<String> set = readList(cfg, key);
            boolean changed = add ? set.add(id.toString()) : set.remove(id.toString());
            if (changed) {
                cfg.set(key, new ArrayList<>(set));
                cfg.save();
            }
            return true;
        } catch (Exception e) {
            LOG.error("[Elements: Economy] Could not update {}.", valuesFile(), e);
            return false;
        }
    }

    private static void log() {
        if (found.isEmpty() && itemValues.isEmpty()) {
            LOG.info("[Elements: Economy] No other currency mods found. Using digital money.");
            return;
        }
        LOG.info("[Elements: Economy] Found {} currency mod(s):", found.size());
        for (KnownCurrency c : found) {
            LOG.info("[Elements: Economy]  - {} ({}) [{}] {}", c.name(), c.modId(), c.kind(), c.note());
        }
        LOG.info("[Elements: Economy] {} item(s) counted as money. Edit config/{} to change values.",
                itemValues.size(), VALUES_FILE);
        if (!Features.on(Features.CURRENCY_USE_DETECTED)) {
            LOG.info("[Elements: Economy] currency.use_detected is off. Found mods are listed only.");
        }
    }
}
