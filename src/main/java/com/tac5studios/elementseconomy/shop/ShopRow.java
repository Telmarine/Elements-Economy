package com.tac5studios.elementseconomy.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;

/**
 * One listing in a shop: an item type and its price.
 * BUY shops: price per bundle of {@code per} items. SELL shops: offer per bundle, up to {@code wanted} more items.
 */
public final class ShopRow {

    public final String key;
    /** One of the item (count 1). Null when it can't be read (its mod was removed). */
    public ItemStack item;
    /** The saved item, kept as-is so nothing is lost if the item can't be read. */
    public JsonElement itemJson;
    public String currency;      // currency id, empty = not priced yet
    public BigInteger price = BigInteger.ZERO;
    public int per = 1;
    public int dailyLimit;       // per player per day, 0 = none
    public boolean enabled;
    public int wanted;           // SELL: how many more the shop accepts

    public ShopRow(String key) {
        this.key = key;
    }

    public boolean priced() {
        return currency != null && !currency.isEmpty() && price.signum() > 0;
    }

    /** Visible to buyers/sellers. */
    public boolean listed() {
        return enabled && priced() && item != null;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("item", itemJson);
        o.addProperty("currency", currency == null ? "" : currency);
        o.addProperty("price", price.toString());
        o.addProperty("per", per);
        o.addProperty("daily_limit", dailyLimit);
        o.addProperty("enabled", enabled);
        o.addProperty("wanted", wanted);
        return o;
    }

    static ShopRow fromJson(String key, JsonObject o) {
        ShopRow r = new ShopRow(key);
        r.itemJson = o.get("item");
        r.currency = o.has("currency") ? o.get("currency").getAsString() : "";
        try {
            r.price = o.has("price") ? new BigInteger(o.get("price").getAsString()) : BigInteger.ZERO;
        } catch (NumberFormatException e) {
            r.price = BigInteger.ZERO;
        }
        r.per = o.has("per") ? Math.max(1, o.get("per").getAsInt()) : 1;
        r.dailyLimit = o.has("daily_limit") ? o.get("daily_limit").getAsInt() : 0;
        r.enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
        r.wanted = o.has("wanted") ? o.get("wanted").getAsInt() : 0;
        return r;
    }
}
