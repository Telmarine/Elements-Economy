package com.tac5studios.elementseconomy.auction;

import com.tac5studios.elementseconomy.config.AuctionConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.integration.NexusHook;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.shop.ShopStock;
import com.tac5studios.elementseconomy.shop.ShopTrade;
import com.tac5studios.elementseconomy.storage.TransactionLog;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.event.AuctionListedEvent;
import com.tac5studios.elementsvault.event.AuctionSoldEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/** Player actions in the auction house. Every step is checked before money or items move. */
public final class AhTrade {

    private AhTrade() {}

    // ---------- rules ----------

    public static boolean blacklisted(ItemStack stack) {
        if (!Features.on(Features.AUCTION, Features.AH_BLACKLIST)) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String entry : AuctionConfig.BLACKLIST.get()) {
            String e = entry.trim();
            if (e.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(e.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            } else if (e.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Listings this player may have at once, or -1 for no limit. */
    public static int limit(ServerPlayer p) {
        if (Perm.has(p, Perm.AH_NO_LIMIT)) return -1;
        int fromPerms = Perm.limit(p, Perm.AH_LIMIT);
        if (fromPerms >= 0) return fromPerms;
        if (Features.on(Features.AUCTION, Features.AH_RANK_LIMITS)) {
            Optional<String> rank = NexusHook.rankOf(p.getUUID());
            if (rank.isPresent()) {
                for (String line : AuctionConfig.RANK_LIMITS.get()) {
                    int eq = line.indexOf('=');
                    if (eq > 0 && line.substring(0, eq).trim().equalsIgnoreCase(rank.get())) {
                        try {
                            return Integer.parseInt(line.substring(eq + 1).trim());
                        } catch (NumberFormatException ignored) {
                            // bad line
                        }
                    }
                }
            }
        }
        return AuctionConfig.DEFAULT_LIMIT.get();
    }

    /** Listing fee for a listing worth {@code value} (0 when off or exempt). */
    public static BigInteger fee(ServerPlayer p, Currency cur, BigInteger value) {
        if (!Features.on(Features.AUCTION, Features.AH_LISTING_FEE) || Perm.has(p, Perm.AH_NO_FEE)) return BigInteger.ZERO;
        if ("percent".equals(AuctionConfig.FEE_MODE.get())) {
            return new BigDecimal(value).multiply(BigDecimal.valueOf(AuctionConfig.FEE_PERCENT.get() / 100.0))
                    .setScale(0, RoundingMode.CEILING).toBigInteger();
        }
        return cur.parse(AuctionConfig.FEE_AMOUNT.get()).orElse(BigInteger.ZERO);
    }

    private static String fmt(Economy e, Currency c, BigInteger amount) {
        return e.format(c.of(amount));
    }

    // ---------- list ----------

    /**
     * Lists the stack the player holds. The item only leaves their hand once every check passed.
     * @param price buy now: per item; auction: starting bid
     */
    public static boolean list(ServerPlayer p, ItemStack expected, boolean auction, BigInteger price, int hours) {
        Economy e = Economy.get();
        if (e == null || e.primaryCurrency() == null) {
            Msg.send(p, "general.currency_unavailable");
            return false;
        }
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty() || !ItemStack.isSameItemSameComponents(hand, expected) || hand.getCount() != expected.getCount()) {
            Msg.send(p, "ah.hold_item");
            return false;
        }
        if (blacklisted(hand)) {
            Msg.send(p, "ah.blacklisted");
            return false;
        }
        int limit = limit(p);
        int active = Auctions.activeCount(p.getUUID());
        if (limit >= 0 && active >= limit) {
            Msg.send(p, "ah.limit", "count", active, "limit", limit);
            return false;
        }
        if (price.signum() <= 0) {
            Msg.send(p, "general.bad_amount", "input", price);
            return false;
        }
        Currency cur = e.primaryCurrency();
        int qty = hand.getCount();
        BigInteger value = auction ? price : price.multiply(BigInteger.valueOf(qty));
        BigInteger fee = fee(p, cur, value);
        if (fee.signum() > 0) {
            Result r = e.withdraw(p.getUUID(), cur.of(fee), Cause.command("/ah sell", p.getUUID()).withReason("listing fee"));
            if (!r.success()) {
                Msg.send(p, "ah.fee_not_enough", "amount", fmt(e, cur, fee));
                return false;
            }
            Msg.send(p, "ah.fee", "amount", fmt(e, cur, fee));
        }

        Listing l = new Listing(Auctions.newId(), p.getUUID(), p.getGameProfile().getName(), auction);
        l.item = hand.copyWithCount(1);
        l.quantity = qty;
        l.left = qty;
        l.currency = cur.id().toString();
        l.price = price;
        l.created = System.currentTimeMillis();
        l.ends = l.created + hours * 3_600_000L;
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Auctions.add(l);

        Msg.send(p, "ah.listed", "count", qty, "item", l.item.getHoverName().getString(), "amount", fmt(e, cur, auction ? price : price));
        TransactionLog.add("AH_LIST", l.sellerName + " listed " + qty + "x " + l.item.getHoverName().getString()
                + (auction ? " (auction from " : " (") + fmt(e, cur, price) + (auction ? ")" : " each)") + " listing " + l.id);
        if (Features.on(Features.API, Features.API_EVENTS)) {
            NeoForge.EVENT_BUS.post(new AuctionListedEvent(p.getUUID(), l.id, l.item.copyWithCount(qty), cur.of(price), auction));
        }
        return true;
    }

    // ---------- buy now ----------

    /** Buys the whole listing (amounts are fixed in the auction house). */
    public static boolean buy(ServerPlayer p, Listing l) {
        int count = l.left;
        Economy e = Economy.get();
        Optional<Currency> cur = Auctions.currency(l);
        if (e == null || cur.isEmpty() || !l.active() || l.auction || l.item == null) {
            Msg.send(p, "ah.gone");
            return false;
        }
        if (l.seller.equals(p.getUUID())) {
            Msg.send(p, "ah.own");
            return false;
        }
        if (count <= 0 || count > l.left) {
            Msg.send(p, "ah.gone");
            return false;
        }
        BigInteger total = l.price.multiply(BigInteger.valueOf(count));
        Money have = e.balance(p.getUUID(), cur.get());
        if (have.amount().compareTo(total) < 0) {
            Msg.send(p, "shop.not_enough", "amount", fmt(e, cur.get(), total), "balance", e.format(have));
            return false;
        }
        if (ShopStock.room(ShopTrade.inventory(p), l.item, count) < count) {
            Msg.send(p, "shop.no_room");
            return false;
        }
        Result paid = e.withdraw(p.getUUID(), cur.get().of(total), Cause.auction(l.id, p.getUUID()));
        if (!paid.success()) {
            Msg.send(p, "shop.not_enough", "amount", fmt(e, cur.get(), total), "balance", e.format(have));
            return false;
        }
        int left = count;
        while (left > 0) {
            int n = Math.min(left, l.item.getMaxStackSize());
            ItemStack st = l.item.copyWithCount(n);
            if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
            left -= n;
        }
        p.containerMenu.broadcastChanges();

        l.left -= count;
        CollectionBox.addMoney(l.seller, l.currency, Auctions.afterTax(l, total));
        if (l.left <= 0) {
            l.status = Listing.Status.SOLD;
            l.closed = System.currentTimeMillis();
        }
        Auctions.save(l);

        String itemName = l.item.getHoverName().getString();
        String amount = fmt(e, cur.get(), total);
        Msg.send(p, "ah.bought", "count", count, "item", itemName, "amount", amount);
        if (Features.on(Features.AH_SALE_ALERTS)) {
            Auctions.tell(l.seller, "ah.sold", "player", p.getGameProfile().getName(), "count", count, "item", itemName, "amount", amount);
        }
        if (Features.on(Features.API, Features.API_EVENTS)) {
            NeoForge.EVENT_BUS.post(new AuctionSoldEvent(l.seller, p.getUUID(), l.id, l.item.copyWithCount(count), cur.get().of(total)));
        }
        return true;
    }

    // ---------- bid ----------

    public static boolean bid(ServerPlayer p, Listing l, BigInteger amount) {
        Economy e = Economy.get();
        Optional<Currency> cur = Auctions.currency(l);
        if (e == null || cur.isEmpty() || !l.active() || !l.auction || l.item == null) {
            Msg.send(p, "ah.gone");
            return false;
        }
        if (l.seller.equals(p.getUUID())) {
            Msg.send(p, "ah.own");
            return false;
        }
        BigInteger min = Auctions.minBid(l);
        if (amount.compareTo(min) < 0) {
            Msg.send(p, "ah.bid_low", "amount", fmt(e, cur.get(), min));
            return false;
        }
        Result paid = e.withdraw(p.getUUID(), cur.get().of(amount), Cause.auction(l.id, p.getUUID()).withReason("bid"));
        if (!paid.success()) {
            Msg.send(p, "pay.not_enough", "balance", e.format(e.balance(p.getUUID(), cur.get())));
            return false;
        }
        Auctions.refundTopBid(l, !p.getUUID().equals(l.topBidder));
        l.topBid = amount;
        l.topBidder = p.getUUID();
        l.bids++;
        Auctions.save(l);
        Msg.send(p, "ah.bid", "amount", fmt(e, cur.get(), amount), "item", l.item.getHoverName().getString());
        if (Features.on(Features.AH_SALE_ALERTS)) {
            Auctions.tell(l.seller, "ah.bid_alert", "player", p.getGameProfile().getName(),
                    "item", l.item.getHoverName().getString(), "amount", fmt(e, cur.get(), amount));
        }
        return true;
    }

    // ---------- cancel / remove ----------

    /** Seller cancels: items go back to their collection box. Auctions with bids can't be cancelled. */
    public static boolean cancel(ServerPlayer p, Listing l) {
        if (!l.active() || !l.seller.equals(p.getUUID())) {
            Msg.send(p, "ah.gone");
            return false;
        }
        if (l.auction && l.bids > 0) {
            Msg.send(p, "ah.cancel_has_bids");
            return false;
        }
        close(l, Listing.Status.CANCELLED);
        Msg.send(p, "ah.cancelled");
        return true;
    }

    /** Staff removes any listing: the top bid is refunded and the items go back to the seller. */
    public static boolean adminRemove(ServerPlayer staff, Listing l) {
        if (!l.active()) {
            Msg.send(staff, "ah.gone");
            return false;
        }
        Auctions.refundTopBid(l, false);
        l.topBidder = null;
        l.topBid = BigInteger.ZERO;
        close(l, Listing.Status.CANCELLED);
        Msg.send(staff, "ah.removed", "player", l.sellerName);
        TransactionLog.add("AH_REMOVE", staff.getGameProfile().getName() + " removed listing " + l.id + " by " + l.sellerName);
        return true;
    }

    private static void close(Listing l, Listing.Status status) {
        if (l.left > 0 && l.item != null) CollectionBox.addItems(l.seller, l.item, l.left, Auctions.registries());
        l.left = 0;
        l.status = status;
        l.closed = System.currentTimeMillis();
        Auctions.save(l);
    }

    // ---------- collect ----------

    /** Pays out money and hands over items that fit. Returns how many things were collected. */
    public static int collectAll(ServerPlayer p) {
        Economy e = Economy.get();
        int n = 0;
        if (e != null) {
            for (Map.Entry<String, BigInteger> m : CollectionBox.money(p.getUUID()).entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(m.getKey());
                Optional<Currency> cur = id == null ? Optional.empty() : e.currency(id);
                if (cur.isEmpty()) continue;
                Result r = e.deposit(p.getUUID(), cur.get().of(m.getValue()), Cause.command("/ah collect", p.getUUID()));
                if (r.success()) {
                    CollectionBox.removeMoney(p.getUUID(), m.getKey());
                    n++;
                }
            }
        }
        var items = CollectionBox.items(p.getUUID(), Auctions.registries());
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack st = items.get(i);
            if (st.isEmpty()) continue;
            if (ShopStock.room(ShopTrade.inventory(p), st, st.getCount()) < st.getCount()) continue;
            p.getInventory().add(st.copy());
            CollectionBox.removeItem(p.getUUID(), i);
            n++;
        }
        p.containerMenu.broadcastChanges();
        return n;
    }
}
