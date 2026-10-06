package com.tac5studios.elementseconomy.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.config.StorageConfig;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementsvault.Money;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player settings and payment history.
 * accounts: { name, pay_toggle, last_seen }    history: [ { time, kind, other, currency, amount, note } ]
 */
public final class Accounts {

    private Accounts() {}

    // ---------- accounts ----------

    private static JsonObject get(UUID id) {
        JsonObject o = Storage.get().get(Collections.ACCOUNTS, id.toString(), JsonObject.class);
        return o == null ? new JsonObject() : o;
    }

    private static void put(UUID id, JsonObject o) {
        Storage.get().put(Collections.ACCOUNTS, id.toString(), o);
    }

    /** Called on login: remember the name and time. */
    public static void seen(ServerPlayer p) {
        JsonObject o = get(p.getUUID());
        o.addProperty("name", p.getGameProfile().getName());
        o.addProperty("last_seen", Instant.now().toString());
        put(p.getUUID(), o);
    }

    /** The player's name: online name, then saved name, then the server's profile cache. */
    public static String name(UUID id) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) return p.getGameProfile().getName();
        }
        JsonObject o = get(id);
        if (o.has("name")) return o.get("name").getAsString();
        if (server != null && server.getProfileCache() != null) {
            Optional<GameProfile> gp = server.getProfileCache().get(id);
            if (gp.isPresent()) return gp.get().getName();
        }
        return id.toString().substring(0, 8);
    }

    /** True when the player accepts payments (default yes). */
    public static boolean acceptsPayments(UUID id) {
        JsonObject o = get(id);
        return !o.has("pay_toggle") || o.get("pay_toggle").getAsBoolean();
    }

    /** Flip payments on/off. Returns the new state. */
    public static boolean togglePayments(UUID id) {
        JsonObject o = get(id);
        boolean now = !acceptsPayments(id);
        o.addProperty("pay_toggle", now);
        put(id, o);
        return now;
    }

    /** Every player with a saved account. */
    public static java.util.List<String> keys() {
        return Storage.get().keys(Collections.ACCOUNTS);
    }

    /** How many currency switches this player's money has been through. -1 when never set (up to date). */
    public static int switchStep(UUID id) {
        JsonObject o = get(id);
        return o.has("switch_step") ? o.get("switch_step").getAsInt() : -1;
    }

    public static void setSwitchStep(UUID id, int step) {
        JsonObject o = get(id);
        o.addProperty("switch_step", step);
        put(id, o);
    }

    // ---------- history ----------

    /** One payment line for /payments. */
    public record Entry(Instant time, String kind, UUID other, String currency, String amount, String note, boolean incoming) {}

    /** Add a payment to a player's history (newest first, trimmed to logs.history_size). */
    public static void record(UUID player, String kind, UUID other, Money money, String note, boolean incoming) {
        int max = StorageConfig.HISTORY_SIZE.get();
        if (max <= 0) return;
        JsonArray list = Storage.get().get(Collections.HISTORY, player.toString(), JsonArray.class);
        if (list == null) list = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("time", Instant.now().toString());
        e.addProperty("kind", kind);
        if (other != null) e.addProperty("other", other.toString());
        e.addProperty("currency", money.currency().id().toString());
        e.addProperty("amount", money.amount().toString());
        if (note != null && !note.isEmpty()) e.addProperty("note", note);
        e.addProperty("in", incoming);
        JsonArray out = new JsonArray();
        out.add(e);
        for (int i = 0; i < list.size() && out.size() < max; i++) out.add(list.get(i));
        Storage.get().put(Collections.HISTORY, player.toString(), out);
    }

    public static List<Entry> history(UUID player) {
        List<Entry> out = new ArrayList<>();
        JsonArray list = Storage.get().get(Collections.HISTORY, player.toString(), JsonArray.class);
        if (list == null) return out;
        for (JsonElement el : list) {
            try {
                JsonObject o = el.getAsJsonObject();
                out.add(new Entry(
                        Instant.parse(o.get("time").getAsString()),
                        o.get("kind").getAsString(),
                        o.has("other") ? UUID.fromString(o.get("other").getAsString()) : null,
                        o.get("currency").getAsString(),
                        o.get("amount").getAsString(),
                        o.has("note") ? o.get("note").getAsString() : "",
                        o.has("in") && o.get("in").getAsBoolean()));
            } catch (RuntimeException ignored) {
                // skip a broken line
            }
        }
        return out;
    }
}
