package com.tac5studios.elementseconomy.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player shop on a container. Money from sales goes into the shop's till; the owner collects it.
 * Sell shops pay players from the till, which the owner fills. The till is never shown to customers.
 */
public final class Shop {

    public final String id;
    public final UUID owner;
    public String ownerName;
    public final ShopType type;
    public String name;
    public final ResourceKey<Level> dimension;
    /** Main block, then the other half of a double chest if there is one. */
    public final List<BlockPos> positions = new ArrayList<>();
    public final Map<String, ShopRow> rows = new LinkedHashMap<>();
    /** Money held by the shop, per currency id. */
    public final Map<String, BigInteger> till = new LinkedHashMap<>();
    @Nullable
    public BlockPos vault;
    /** Owner's switch: customers reach the shop screen through the owner's claim, with no claim warning. */
    public boolean claimAccess;
    /** Owner's open/closed switch. Closed shops don't trade. */
    public boolean open = true;
    public long created;

    public Shop(String id, UUID owner, String ownerName, ShopType type, String name, ResourceKey<Level> dimension) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.type = type;
        this.name = name;
        this.dimension = dimension;
    }

    public BlockPos pos() {
        return positions.get(0);
    }

    public BigInteger till(String currency) {
        return till.getOrDefault(currency, BigInteger.ZERO);
    }

    public void addTill(String currency, BigInteger amount) {
        BigInteger v = till(currency).add(amount);
        if (v.signum() <= 0) till.remove(currency);
        else till.put(currency, v);
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("owner", owner.toString());
        o.addProperty("owner_name", ownerName);
        o.addProperty("type", type.name().toLowerCase());
        o.addProperty("name", name);
        o.addProperty("dimension", dimension.location().toString());
        JsonArray pos = new JsonArray();
        for (BlockPos p : positions) pos.add(p.asLong());
        o.add("pos", pos);
        JsonObject r = new JsonObject();
        rows.forEach((k, v) -> r.add(k, v.toJson()));
        o.add("rows", r);
        JsonObject t = new JsonObject();
        till.forEach((k, v) -> t.addProperty(k, v.toString()));
        o.add("till", t);
        if (vault != null) o.addProperty("vault", vault.asLong());
        o.addProperty("claim_access", claimAccess);
        o.addProperty("open", open);
        o.addProperty("created", created);
        return o;
    }

    static Shop fromJson(String id, JsonObject o) {
        ResourceLocation dim = ResourceLocation.parse(o.get("dimension").getAsString());
        Shop s = new Shop(id, UUID.fromString(o.get("owner").getAsString()),
                o.has("owner_name") ? o.get("owner_name").getAsString() : "?",
                ShopType.valueOf(o.get("type").getAsString().toUpperCase()),
                o.has("name") ? o.get("name").getAsString() : "Shop",
                ResourceKey.create(Registries.DIMENSION, dim));
        for (JsonElement e : o.getAsJsonArray("pos")) s.positions.add(BlockPos.of(e.getAsLong()));
        if (o.has("rows")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rows").entrySet()) {
                s.rows.put(e.getKey(), ShopRow.fromJson(e.getKey(), e.getValue().getAsJsonObject()));
            }
        }
        if (o.has("till")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("till").entrySet()) {
                try {
                    s.till.put(e.getKey(), new BigInteger(e.getValue().getAsString()));
                } catch (NumberFormatException ignored) {
                    // skip a broken amount
                }
            }
        }
        if (o.has("vault")) s.vault = BlockPos.of(o.get("vault").getAsLong());
        s.created = o.has("created") ? o.get("created").getAsLong() : 0;
        s.claimAccess = o.has("claim_access") && o.get("claim_access").getAsBoolean();
        s.open = !o.has("open") || o.get("open").getAsBoolean();
        return s;
    }
}
