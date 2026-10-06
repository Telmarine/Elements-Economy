package com.tac5studios.elementseconomy.shop;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;

import java.time.LocalDate;
import java.util.UUID;

/** Per-player daily counts for rows with a daily limit. Resets each day. Collection "daily", key = shop id. */
public final class ShopDaily {

    private ShopDaily() {}

    private static JsonObject today(String shopId) {
        JsonObject o = Storage.get().get(Collections.DAILY, shopId, JsonObject.class);
        String day = LocalDate.now().toString();
        if (o == null || !o.has("day") || !day.equals(o.get("day").getAsString())) {
            o = new JsonObject();
            o.addProperty("day", day);
            o.add("counts", new JsonObject());
        }
        return o;
    }

    public static int used(Shop s, ShopRow r, UUID player) {
        JsonObject c = today(s.id).getAsJsonObject("counts");
        String k = r.key + "|" + player;
        return c.has(k) ? c.get(k).getAsInt() : 0;
    }

    public static void add(Shop s, ShopRow r, UUID player, int n) {
        JsonObject o = today(s.id);
        JsonObject c = o.getAsJsonObject("counts");
        String k = r.key + "|" + player;
        c.addProperty(k, (c.has(k) ? c.get(k).getAsInt() : 0) + n);
        Storage.get().put(Collections.DAILY, s.id, o);
    }

    /** Items sold today across all players for a row (owner tooltip). */
    public static int soldToday(Shop s, ShopRow r) {
        JsonObject c = today(s.id).getAsJsonObject("counts");
        int n = 0;
        for (String k : c.keySet()) if (k.startsWith(r.key + "|")) n += c.get(k).getAsInt();
        return n;
    }
}
