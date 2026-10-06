package com.tac5studios.elementseconomy.auction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.UUID;

/**
 * One auction house listing. Buy-now listings sell any part of their quantity at {@code price} each.
 * Auctions sell the whole quantity to the highest bidder when time runs out; the top bid is held
 * by Economy until then.
 */
public final class Listing {

    public enum Status { ACTIVE, SOLD, EXPIRED, CANCELLED }

    public final String id;
    public final UUID seller;
    public String sellerName;
    public final boolean auction;
    /** The item, one count; the amount left is {@link #left}. */
    public ItemStack item;
    public JsonElement itemJson;
    public int quantity;
    public int left;
    public String currency;
    /** Buy now: price per item. Auction: starting bid for the whole lot. */
    public BigInteger price;
    public BigInteger topBid = BigInteger.ZERO;
    @Nullable
    public UUID topBidder;
    public int bids;
    public long created;
    public long ends;
    public Status status = Status.ACTIVE;
    public long closed;

    public Listing(String id, UUID seller, String sellerName, boolean auction) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.auction = auction;
    }

    public boolean active() {
        return status == Status.ACTIVE;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("seller", seller.toString());
        o.addProperty("seller_name", sellerName);
        o.addProperty("auction", auction);
        o.add("item", itemJson);
        o.addProperty("quantity", quantity);
        o.addProperty("left", left);
        o.addProperty("currency", currency);
        o.addProperty("price", price.toString());
        o.addProperty("top_bid", topBid.toString());
        if (topBidder != null) o.addProperty("top_bidder", topBidder.toString());
        o.addProperty("bids", bids);
        o.addProperty("created", created);
        o.addProperty("ends", ends);
        o.addProperty("status", status.name().toLowerCase());
        o.addProperty("closed", closed);
        return o;
    }

    static Listing fromJson(String id, JsonObject o) {
        Listing l = new Listing(id, UUID.fromString(o.get("seller").getAsString()),
                o.has("seller_name") ? o.get("seller_name").getAsString() : "?",
                o.has("auction") && o.get("auction").getAsBoolean());
        l.itemJson = o.get("item");
        l.quantity = o.get("quantity").getAsInt();
        l.left = o.has("left") ? o.get("left").getAsInt() : l.quantity;
        l.currency = o.get("currency").getAsString();
        l.price = new BigInteger(o.get("price").getAsString());
        l.topBid = o.has("top_bid") ? new BigInteger(o.get("top_bid").getAsString()) : BigInteger.ZERO;
        l.topBidder = o.has("top_bidder") ? UUID.fromString(o.get("top_bidder").getAsString()) : null;
        l.bids = o.has("bids") ? o.get("bids").getAsInt() : 0;
        l.created = o.get("created").getAsLong();
        l.ends = o.get("ends").getAsLong();
        l.status = Status.valueOf(o.get("status").getAsString().toUpperCase());
        l.closed = o.has("closed") ? o.get("closed").getAsLong() : 0;
        return l;
    }
}
