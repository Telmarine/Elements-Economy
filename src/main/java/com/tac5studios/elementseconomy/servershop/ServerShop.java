package com.tac5studios.elementseconomy.servershop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.shop.ShopType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A server shop: run by staff, no owner, no container, no till. Buy shops sell to players
 * (the money leaves the economy); sell shops buy from players (the server pays).
 * Opens from /shop open, a linked NPC, a staff-made villager, or a linked block.
 */
public final class ServerShop {

    /** How a shop opens in the world. */
    public record Link(String kind, String target) {
        public static final String NPC = "npc";
        public static final String VILLAGER = "villager";
        public static final String BLOCK = "block";
    }

    public final String id;      // lower-case name, used in commands
    public String name;          // shown in the menu title
    public final ShopType type;
    public boolean open = true;
    public final Map<String, ServerRow> rows = new LinkedHashMap<>();
    public final List<Link> links = new ArrayList<>();

    public ServerShop(String id, String name, ShopType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type.name().toLowerCase());
        o.addProperty("open", open);
        JsonObject r = new JsonObject();
        rows.forEach((k, v) -> r.add(k, v.toJson()));
        o.add("rows", r);
        JsonArray l = new JsonArray();
        for (Link link : links) {
            JsonObject lo = new JsonObject();
            lo.addProperty("kind", link.kind());
            lo.addProperty("target", link.target());
            l.add(lo);
        }
        o.add("links", l);
        return o;
    }

    static ServerShop fromJson(String id, JsonObject o) {
        ServerShop s = new ServerShop(id, o.has("name") ? o.get("name").getAsString() : id,
                ShopType.valueOf(o.get("type").getAsString().toUpperCase()));
        s.open = !o.has("open") || o.get("open").getAsBoolean();
        if (o.has("rows")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rows").entrySet()) {
                s.rows.put(e.getKey(), ServerRow.fromJson(e.getKey(), e.getValue().getAsJsonObject()));
            }
        }
        if (o.has("links")) {
            for (JsonElement e : o.getAsJsonArray("links")) {
                JsonObject lo = e.getAsJsonObject();
                s.links.add(new Link(lo.get("kind").getAsString(), lo.get("target").getAsString()));
            }
        }
        return s;
    }
}
