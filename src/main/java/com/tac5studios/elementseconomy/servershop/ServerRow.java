package com.tac5studios.elementseconomy.servershop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One listing in a server shop. Unlimited by default.
 * With stock limits on (server owner's choice): {@code stock} left, topped up by {@code refillAmount}
 * every {@code refillMinutes} up to {@code stockMax}. For sell shops, stock = how many more the server buys.
 * With rank prices on: a price per Nexus rank overrides the normal price.
 */
public final class ServerRow {

    public final String key;
    public ItemStack item;
    public JsonElement itemJson;
    public String currency = "";
    public BigInteger price = BigInteger.ZERO;
    public int per = 1;
    public boolean enabled;
    public int stockMax;       // 0 = unlimited
    public int stock;
    public int refillAmount;
    public int refillMinutes;
    public long lastRefill;
    public final Map<String, BigInteger> rankPrices = new LinkedHashMap<>();

    public ServerRow(String key) {
        this.key = key;
    }

    public boolean priced() {
        return !currency.isEmpty() && price.signum() > 0;
    }

    public boolean listed() {
        return enabled && priced() && item != null;
    }

    public boolean limited() {
        return stockMax > 0;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("item", itemJson);
        o.addProperty("currency", currency);
        o.addProperty("price", price.toString());
        o.addProperty("per", per);
        o.addProperty("enabled", enabled);
        o.addProperty("stock_max", stockMax);
        o.addProperty("stock", stock);
        o.addProperty("refill_amount", refillAmount);
        o.addProperty("refill_minutes", refillMinutes);
        o.addProperty("last_refill", lastRefill);
        JsonObject rp = new JsonObject();
        rankPrices.forEach((k, v) -> rp.addProperty(k, v.toString()));
        o.add("rank_prices", rp);
        return o;
    }

    static ServerRow fromJson(String key, JsonObject o) {
        ServerRow r = new ServerRow(key);
        r.itemJson = o.get("item");
        r.currency = o.has("currency") ? o.get("currency").getAsString() : "";
        r.price = big(o, "price");
        r.per = o.has("per") ? Math.max(1, o.get("per").getAsInt()) : 1;
        r.enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
        r.stockMax = o.has("stock_max") ? o.get("stock_max").getAsInt() : 0;
        r.stock = o.has("stock") ? o.get("stock").getAsInt() : 0;
        r.refillAmount = o.has("refill_amount") ? o.get("refill_amount").getAsInt() : 0;
        r.refillMinutes = o.has("refill_minutes") ? o.get("refill_minutes").getAsInt() : 0;
        r.lastRefill = o.has("last_refill") ? o.get("last_refill").getAsLong() : 0;
        if (o.has("rank_prices")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rank_prices").entrySet()) {
                try {
                    r.rankPrices.put(e.getKey(), new BigInteger(e.getValue().getAsString()));
                } catch (NumberFormatException ignored) {
                    // skip a broken price
                }
            }
        }
        return r;
    }

    private static BigInteger big(JsonObject o, String k) {
        try {
            return o.has(k) ? new BigInteger(o.get(k).getAsString()) : BigInteger.ZERO;
        } catch (NumberFormatException e) {
            return BigInteger.ZERO;
        }
    }
}
