package com.tac5studios.elementseconomy.currency;

import net.neoforged.api.distmarker.Dist;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All-in-one currency detection. One switch (currency.auto_detect) checks every mod in {@link KnownCurrencies}.
 * A mod counts as found when its mod ID is loaded and one of its marker classes exists.
 *
 * <p>config/elements_economy/currency_values.toml is filled in by Economy and only lists what is in use: the items of
 * every currency mod it found, and the vanilla items picked with /economy currency setup. Each line is a full item ID
 * and what one of that item is worth in the smallest unit. Lines for mods that are gone are dropped (old file backed up).
 *
 * <p>When no currency mod is found, admins are asked in game whether the server uses digital money or vanilla items
 * (/economy currency setup, {@link CurrencyCommands}). Picking the vanilla items and confirming them is the approval: vanilla items are never
 * money otherwise. The choice is kept in the data store.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class CurrencyDetector {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String VALUES_FILE = "elements_economy/currency_values.toml";
    private static final String VANILLA = "minecraft";
    private static final String TABLE = "values";
    private static final String SETUP_KEY = "currency_setup";

    /** The setup choices. */
    public static final String DIGITAL = "digital", VANILLA_ITEMS = "vanilla";

    private static List<KnownCurrency> found = List.of();
    private static Map<Item, Long> itemValues = Map.of();
    private static Map<String, List<Map.Entry<Item, Long>>> denominations = Map.of();
    /** Every line in the values file: full item ID -> value (0 = listed but not counted). */
    private static Map<String, Long> lines = Map.of();

    private CurrencyDetector() {}

    // ------------------------------------------------------------------ events

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        refresh();
    }

    /** Detection at startup and on /economy reload. Does nothing (finds nothing) when auto_detect is off. */
    public static void refresh() {
        if (!Features.on(Features.CURRENCY_AUTO_DETECT)) {
            found = List.of();
            itemValues = Map.of();
            denominations = Map.of();
            lines = Map.of();
            return;
        }
        detect();
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (needsSetup()) {
            LOG.warn("[Elements: Economy] No currency mod was found. Pick this server's currency in game with "
                    + "/economy currency setup (digital money or vanilla items). Digital money is used until then.");
        }
        askAllAdmins(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ask(p);
        }
    }

    // ------------------------------------------------------------------ public

    /** Finds currency mods, brings the values file up to date and loads the values. Safe to call again on reload. */
    public static void detect() {
        List<KnownCurrency> hits = new ArrayList<>();
        for (KnownCurrency c : KnownCurrencies.ALL) {
            if (isPresent(c)) {
                hits.add(c);
            }
        }
        found = java.util.Collections.unmodifiableList(hits);
        sync(null, null);
        if (Features.on(Features.CURRENCY_DETECT_LOG)) {
            log();
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

    /** Every item-currency namespace that has at least one counted item (e.g. "minecraft" once vanilla items are picked). */
    public static Set<String> itemCurrencies() {
        return denominations.keySet();
    }

    /** Items of one item currency, largest value first. Empty when the currency has no counted items. */
    public static List<Map.Entry<Item, Long>> denominations(String namespace) {
        return denominations.getOrDefault(namespace, List.of());
    }

    /** Every line in the values file, full item ID -> value, in file order. */
    public static Map<String, Long> lines() {
        return lines;
    }

    /** The setup choice: {@link #DIGITAL}, {@link #VANILLA_ITEMS}, or null when nobody picked yet. */
    public static String setupChoice() {
        try {
            if (!Storage.running()) return null;
            JsonObject o = Storage.get().get(Collections.META, SETUP_KEY, JsonObject.class);
            return o != null && o.has("choice") ? o.get("choice").getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** True when admins still have to pick the server's currency: no currency mod and no choice yet. */
    public static boolean needsSetup() {
        return found.isEmpty() && setupChoice() == null;
    }

    /** Digital money: vanilla items stop counting and leave the values file. */
    public static boolean chooseDigital() {
        saveChoice(DIGITAL);
        return sync(Map.of(), null);
    }

    /** Vanilla items: exactly these items (full ID -> value) count as money and are written to the values file. */
    public static boolean chooseVanilla(Map<ResourceLocation, Long> items) {
        saveChoice(VANILLA_ITEMS);
        Map<String, Long> vanilla = new LinkedHashMap<>();
        items.forEach((id, v) -> vanilla.put(id.toString(), v));
        return sync(vanilla, null);
    }

    /**
     * Changes one value. A listed item (a found mod's item or a picked vanilla item) gets the new value; a vanilla item
     * that isn't listed yet is added when the server uses vanilla items. For vanilla items 0 removes the line.
     * Returns false when the item can't be set here.
     */
    public static boolean setValue(ResourceLocation id, long value) {
        String key = id.toString();
        if (isVanilla(id)) {
            if (!VANILLA_ITEMS.equals(setupChoice()) || !BuiltInRegistries.ITEM.containsKey(id)) return false;
            Map<String, Long> vanilla = new LinkedHashMap<>();
            lines.forEach((k, v) -> { if (k.startsWith(VANILLA + ":")) vanilla.put(k, v); });
            if (value <= 0) vanilla.remove(key);
            else vanilla.put(key, value);
            return sync(vanilla, null);
        }
        if (!lines.containsKey(key)) return false;
        return sync(null, Map.of(key, Math.max(0, value)));
    }

    /** Sends the setup question to one admin, if the server still needs it. */
    public static void ask(ServerPlayer player) {
        if (!needsSetup() || !Perm.has(player, Perm.CURRENCY_SETUP)) {
            return;
        }
        player.sendSystemMessage(askMessage());
    }

    /** Sends the setup question to every online admin. */
    public static void askAllAdmins(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ask(p);
        }
    }

    /** The question with its two buttons. */
    public static Component askMessage() {
        return Msg.join(
                Msg.text("currency.setup_ask"),
                Msg.button("currency.setup_digital_button", "currency.setup_digital_hover", "/economy currency setup digital"),
                Msg.button("currency.setup_vanilla_button", "currency.setup_vanilla_hover", "/economy currency setup vanilla"));
    }

    // ------------------------------------------------------------------ internals

    private static void saveChoice(String choice) {
        if (!Storage.running()) return;
        JsonObject o = new JsonObject();
        o.addProperty("choice", choice);
        Storage.get().put(Collections.META, SETUP_KEY, o);
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

    /**
     * Brings the values file up to date and loads it.
     *
     * @param vanilla  the vanilla lines to write (full ID -> value), or null to keep the file's own
     * @param changes  value changes for listed mod items (full ID -> value), or null
     */
    private static boolean sync(Map<String, Long> vanilla, Map<String, Long> changes) {
        Path file = valuesFile();
        boolean ok = true;
        Map<String, Long> out = new LinkedHashMap<>();
        Map<String, String> comments = new LinkedHashMap<>(); // first key of each block -> comment above it
        try {
            Files.createDirectories(file.getParent());
            String oldText = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            Read old = read(oldText);
            com.tac5studios.elementseconomy.config.FileBackups.prune(file);
            String choice = setupChoice();

            // Files from before 1.0.2: approved vanilla items carry over as the setup choice.
            if (old.legacyApproved != null && !old.legacyApproved.isEmpty() && choice == null) {
                saveChoice(VANILLA_ITEMS);
                choice = VANILLA_ITEMS;
            }

            // The items of every currency mod found. Values from the file win over the built-in defaults.
            for (KnownCurrency c : found) {
                if (!c.hasItems()) continue;
                boolean first = true;
                for (Map.Entry<String, Long> e : c.items().entrySet()) {
                    String id = c.modId() + ":" + e.getKey();
                    if (VANILLA.equals(c.modId())) continue;
                    long value = old.values.getOrDefault(id, e.getValue());
                    if (changes != null && changes.containsKey(id)) value = changes.get(id);
                    if (first) {
                        comments.put(id, c.name() + (c.note().isEmpty() ? "" : " - " + c.note()));
                        first = false;
                    }
                    out.put(id, value);
                }
            }

            // Vanilla items, only when the server picked them.
            Map<String, Long> vanillaLines = new LinkedHashMap<>();
            if (VANILLA_ITEMS.equals(choice)) {
                if (vanilla != null) {
                    vanillaLines.putAll(vanilla);
                } else {
                    old.values.forEach((k, v) -> {
                        if (k.startsWith(VANILLA + ":") && (old.legacyApproved == null || old.legacyApproved.contains(k))) {
                            vanillaLines.put(k, v);
                        }
                    });
                }
            }
            boolean firstVanilla = true;
            for (Map.Entry<String, Long> e : vanillaLines.entrySet()) {
                if (firstVanilla) {
                    comments.put(e.getKey(), "Vanilla items, picked with /economy currency setup");
                    firstVanilla = false;
                }
                out.put(e.getKey(), e.getValue());
            }

            // Write only when the lines changed (owner comments survive otherwise), backing up the old file first.
            if (oldText == null || old.legacy || !old.values.equals(out)) {
                // Back up only when Economy changes the file on its own (old format, lines of a removed mod dropped,
                // a new mod's lines added). Changes an admin makes in chat are saved without a backup.
                boolean byAdmin = vanilla != null || changes != null;
                if (oldText != null && !byAdmin) {
                    String backup = com.tac5studios.elementseconomy.config.FileBackups.take(file);
                    LOG.info("[Elements: Economy] currency_values.toml brought up to date. Old file saved as {}.", backup);
                }
                Path tmp = file.resolveSibling("currency_values.toml.tmp");
                Files.writeString(tmp, write(out, comments, choice), StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOG.error("[Elements: Economy] Could not read or write {}. Item currencies are off until it is fixed.", file, e);
            out.clear();
            ok = false;
        }

        Map<Item, Long> values = new HashMap<>();
        for (Map.Entry<String, Long> e : out.entrySet()) {
            if (e.getValue() <= 0) continue;
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                LOG.warn("[Elements: Economy] Currency item {} not found. Skipped.", e.getKey());
                continue;
            }
            values.put(BuiltInRegistries.ITEM.get(id), e.getValue());
        }
        lines = java.util.Collections.unmodifiableMap(out);
        itemValues = java.util.Collections.unmodifiableMap(values);
        denominations = buildDenominations(values);
        return ok;
    }

    /** What the old file holds. */
    private record Read(Map<String, Long> values, boolean legacy, Set<String> legacyApproved) {}

    /**
     * Reads the values file. Current files have one [values] table of full item IDs. Files from before 1.0.2 have one
     * table per mod with short item names, a [minecraft] table and a [confirmed] list; those are read too.
     */
    private static Read read(String text) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return new Read(values, false, null);
        Config cfg = new TomlParser().parse(text);
        if (cfg.get(TABLE) instanceof UnmodifiableConfig table) {
            for (UnmodifiableConfig.Entry e : table.entrySet()) {
                if (e.getRawValue() instanceof Number n) values.put(e.getKey(), n.longValue());
            }
            return new Read(values, false, null);
        }
        Set<String> approved = new LinkedHashSet<>();
        if (cfg.get(List.of("confirmed", "vanilla")) instanceof List<?> l) {
            for (Object o : l) approved.add(String.valueOf(o));
        }
        for (UnmodifiableConfig.Entry section : cfg.entrySet()) {
            if (!(section.getRawValue() instanceof UnmodifiableConfig sc) || section.getKey().equals("confirmed")) continue;
            for (UnmodifiableConfig.Entry e : sc.entrySet()) {
                if (!(e.getRawValue() instanceof Number n)) continue;
                String id = e.getKey().indexOf(':') >= 0 ? e.getKey() : section.getKey() + ":" + e.getKey();
                values.put(id, n.longValue());
            }
        }
        return new Read(values, true, approved);
    }

    /** The file text: a short header, then one [values] table of full item IDs, grouped by mod with a comment each. */
    private static String write(Map<String, Long> out, Map<String, String> comments, String choice) {
        StringBuilder b = new StringBuilder();
        b.append("# Elements: Economy - currency values\n");
        b.append("# What one of each currency item is worth, in your smallest unit. 0 = not counted.\n");
        b.append("# Economy fills this file in itself: the items of each currency mod it finds, and the vanilla items\n");
        b.append("# picked with /economy currency setup. Change values here and run /economy reload, or in game with\n");
        b.append("# /economy currency values. Lines for currency mods that are removed from the server are dropped\n");
        b.append("# (the old file is backed up first).\n\n");
        b.append('[').append(TABLE).append("]\n");
        if (out.isEmpty()) {
            b.append(DIGITAL.equals(choice) ? "\t# Nothing to list: this server uses digital money.\n"
                    : "\t# Nothing to list: no currency mod was found. Pick the server's currency with /economy currency setup.\n");
        }
        boolean firstBlock = true;
        for (Map.Entry<String, Long> e : out.entrySet()) {
            String comment = comments.get(e.getKey());
            if (comment != null) {
                if (!firstBlock) b.append('\n');
                b.append("\t# ").append(comment.replace('\n', ' ')).append('\n');
                firstBlock = false;
            }
            b.append("\t\"").append(e.getKey()).append("\" = ").append(e.getValue()).append('\n');
        }
        return b.toString();
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
        return java.util.Collections.unmodifiableMap(frozen);
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
        LOG.info("[Elements: Economy] {} item(s) counted as money. See config/{} or /economy currency values.",
                itemValues.size(), VALUES_FILE);
        if (!Features.on(Features.CURRENCY_USE_DETECTED)) {
            LOG.info("[Elements: Economy] currency.use_detected is off. Found mods are listed only.");
        }
    }
}
