package com.tac5studios.elementseconomy.auction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.ItemData;
import com.tac5studios.elementseconomy.storage.Storage;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money and items waiting for a player (won auctions, sales, refunds, expired listings).
 * Collection "collect", key = player UUID, value = { items: [...], money: { currency: amount } }.
 * Items that can't be read (mod removed) stay in the box as saved data.
 */
public final class CollectionBox {

    private CollectionBox() {}

    private static JsonObject box(UUID player) {
        JsonObject o = Storage.get().get(Collections.COLLECT, player.toString(), JsonObject.class);
        if (o == null) o = new JsonObject();
        if (!o.has("items")) o.add("items", new JsonArray());
        if (!o.has("money")) o.add("money", new JsonObject());
        return o;
    }

    private static void save(UUID player, JsonObject o) {
        if (o.getAsJsonArray("items").isEmpty() && o.getAsJsonObject("money").isEmpty()) {
            Storage.get().remove(Collections.COLLECT, player.toString());
        } else {
            Storage.get().put(Collections.COLLECT, player.toString(), o);
        }
        Storage.moneyChanged();
    }

    public static void addItem(UUID player, ItemStack stack, HolderLookup.Provider reg) {
        if (stack.isEmpty()) return;
        JsonObject o = box(player);
        o.getAsJsonArray("items").add(ItemData.write(stack, reg));
        save(player, o);
    }

    /** Adds whole stacks of an item (splits by max stack size). */
    public static void addItems(UUID player, ItemStack one, int count, HolderLookup.Provider reg) {
        int max = Math.max(1, one.getMaxStackSize());
        while (count > 0) {
            int n = Math.min(max, count);
            addItem(player, one.copyWithCount(n), reg);
            count -= n;
        }
    }

    public static void addMoney(UUID player, String currency, BigInteger amount) {
        if (amount.signum() <= 0) return;
        JsonObject o = box(player);
        JsonObject m = o.getAsJsonObject("money");
        BigInteger now = m.has(currency) ? new BigInteger(m.get(currency).getAsString()) : BigInteger.ZERO;
        m.addProperty(currency, now.add(amount).toString());
        save(player, o);
    }

    public static boolean isEmpty(UUID player) {
        JsonObject o = Storage.get().get(Collections.COLLECT, player.toString(), JsonObject.class);
        return o == null || (o.has("items") && o.getAsJsonArray("items").isEmpty()
                && o.has("money") && o.getAsJsonObject("money").isEmpty());
    }

    /** Readable items in the box, in order (unreadable ones are skipped here but kept). */
    public static List<ItemStack> items(UUID player, HolderLookup.Provider reg) {
        List<ItemStack> out = new ArrayList<>();
        for (JsonElement e : box(player).getAsJsonArray("items")) {
            ItemStack s = ItemData.read(e, reg);
            out.add(s == null ? ItemStack.EMPTY : s);
        }
        return out;
    }

    public static Map<String, BigInteger> money(UUID player) {
        Map<String, BigInteger> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : box(player).getAsJsonObject("money").entrySet()) {
            try {
                out.put(e.getKey(), new BigInteger(e.getValue().getAsString()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    /** Remove the item at a position (after it was given out). */
    public static void removeItem(UUID player, int index) {
        JsonObject o = box(player);
        JsonArray a = o.getAsJsonArray("items");
        if (index >= 0 && index < a.size()) a.remove(index);
        save(player, o);
    }

    /** Takes {@code amount} of one currency out of the box (after it was paid out). Anything left stays. */
    public static void takeMoney(UUID player, String currency, BigInteger amount) {
        JsonObject o = box(player);
        JsonObject m = o.getAsJsonObject("money");
        BigInteger now = m.has(currency) ? new BigInteger(m.get(currency).getAsString()) : BigInteger.ZERO;
        BigInteger left = now.subtract(amount);
        if (left.signum() > 0) m.addProperty(currency, left.toString());
        else m.remove(currency);
        save(player, o);
    }

    public static void removeMoney(UUID player, String currency) {
        JsonObject o = box(player);
        o.getAsJsonObject("money").remove(currency);
        save(player, o);
    }
}
