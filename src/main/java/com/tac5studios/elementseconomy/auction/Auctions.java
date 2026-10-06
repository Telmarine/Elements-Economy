package com.tac5studios.elementseconomy.auction;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.ElementsEconomy;
import com.tac5studios.elementseconomy.config.AuctionConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.ItemData;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementseconomy.storage.TransactionLog;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.event.AuctionSoldEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every auction house listing (collection "listings"), the end-of-auction timer, and the
 * money side of finishing a sale: proceeds and refunds go to collection boxes, so offline
 * players and coin currencies are always paid safely.
 */
@EventBusSubscriber(modid = ElementsEconomy.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class Auctions {

    private static final Map<String, Listing> LISTINGS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static HolderLookup.Provider registries;
    private static MinecraftServer server;
    private static int ticks;

    private Auctions() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        LISTINGS.clear();
        server = e.getServer();
        registries = server.registryAccess();
        if (!Storage.running()) return;
        for (String id : Storage.get().keys(Collections.LISTINGS)) {
            JsonObject o = Storage.get().get(Collections.LISTINGS, id, JsonObject.class);
            if (o == null) continue;
            try {
                Listing l = Listing.fromJson(id, o);
                l.item = ItemData.read(l.itemJson, registries);
                LISTINGS.put(id, l);
            } catch (RuntimeException ex) {
                ElementsEconomy.LOGGER.error("[Economy] Listing {} could not be loaded. Its data is kept.", id, ex);
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        LISTINGS.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Features.on(Features.AUCTION) && Storage.running()
                && !CollectionBox.isEmpty(p.getUUID())) {
            Msg.send(p, "ah.waiting");
        }
    }

    /** Ends listings whose time is up (every second) and clears old history (every minute). */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (++ticks % 20 != 0 || !Features.on(Features.AUCTION) || !Storage.running()) return;
        long now = System.currentTimeMillis();
        for (Listing l : new ArrayList<>(LISTINGS.values())) {
            // A listing whose item can't be read (its mod was removed) waits until the mod is back.
            if (l.active() && l.item != null && now >= l.ends) finish(l);
        }
        if (ticks >= 20 * 60) {
            ticks = 0;
            long keep = AuctionConfig.HISTORY_DAYS.get() * 86_400_000L;
            for (Listing l : new ArrayList<>(LISTINGS.values())) {
                if (!l.active() && now - l.closed > keep) {
                    LISTINGS.remove(l.id);
                    Storage.get().remove(Collections.LISTINGS, l.id);
                }
            }
        }
    }

    // ---------- lookups ----------

    public static HolderLookup.Provider registries() {
        return registries;
    }

    @Nullable
    public static Listing get(String id) {
        return LISTINGS.get(id);
    }

    public static Collection<Listing> all() {
        return LISTINGS.values();
    }

    public static List<Listing> active() {
        List<Listing> out = new ArrayList<>();
        for (Listing l : LISTINGS.values()) if (l.active() && l.item != null) out.add(l);
        return out;
    }

    public static List<Listing> bySeller(UUID seller) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : LISTINGS.values()) if (l.seller.equals(seller)) out.add(l);
        return out;
    }

    public static int activeCount(UUID seller) {
        int n = 0;
        for (Listing l : LISTINGS.values()) if (l.active() && l.seller.equals(seller)) n++;
        return n;
    }

    public static String newId() {
        String id;
        do {
            id = Long.toString(Math.abs(RANDOM.nextLong()), 36);
            id = id.length() > 8 ? id.substring(0, 8) : id;
        } while (LISTINGS.containsKey(id));
        return id;
    }

    public static Optional<Currency> currency(Listing l) {
        Economy e = Economy.get();
        ResourceLocation id = ResourceLocation.tryParse(l.currency);
        return e == null || id == null ? Optional.empty() : e.currency(id);
    }

    // ---------- changes ----------

    public static void add(Listing l) {
        LISTINGS.put(l.id, l);
        save(l);
    }

    public static void save(Listing l) {
        if (l.item != null && registries != null) l.itemJson = ItemData.write(l.item, registries);
        Storage.get().put(Collections.LISTINGS, l.id, l.toJson());
        Storage.moneyChanged();
    }

    /** Lowest bid that will be accepted now. */
    public static BigInteger minBid(Listing l) {
        if (l.bids == 0) return l.price;
        Optional<Currency> cur = currency(l);
        BigInteger min = cur.flatMap(c -> Economy.configAmount(c, AuctionConfig.BID_STEP_MIN.get())).orElse(BigInteger.ONE);
        BigInteger pct = new BigDecimal(l.topBid).multiply(BigDecimal.valueOf(AuctionConfig.BID_STEP_PERCENT.get() / 100.0))
                .setScale(0, RoundingMode.CEILING).toBigInteger();
        BigInteger next = l.topBid.add(min.max(pct).max(BigInteger.ONE));
        return cur.map(c -> Economy.roundUp(c, next)).orElse(next); // coin currencies: whole coins only
    }

    /** Proceeds after sales tax (when on, and the seller doesn't have ah.notax while online). */
    static BigInteger afterTax(Listing l, BigInteger gross) {
        if (!Features.on(Features.AUCTION, Features.AH_SALES_TAX)) return gross;
        ServerPlayer seller = server == null ? null : server.getPlayerList().getPlayer(l.seller);
        if (seller != null && com.tac5studios.elementseconomy.perms.Perm.has(seller, com.tac5studios.elementseconomy.perms.Perm.AH_NO_TAX)) {
            return gross;
        }
        BigInteger tax = new BigDecimal(gross).multiply(BigDecimal.valueOf(AuctionConfig.TAX_PERCENT.get() / 100.0))
                .setScale(0, RoundingMode.FLOOR).toBigInteger();
        // Coin currencies: round the seller's share down to whole coins so none of it is stranded.
        Optional<Currency> cur = currency(l);
        if (cur.isPresent()) tax = gross.subtract(Economy.payable(cur.get(), gross.subtract(tax)));
        if (tax.signum() > 0) TransactionLog.add("AH_TAX", l.sellerName + " " + tax + " (" + l.currency + ") listing " + l.id);
        return gross.subtract(tax);
    }

    /** Time is up: auction goes to the top bidder; anything unsold goes back to the seller. */
    static void finish(Listing l) {
        if (l.item == null) return; // never hand out or drop an item that can't be read
        l.closed = System.currentTimeMillis();
        String itemName = l.item == null ? "?" : l.item.getHoverName().getString();
        if (l.auction && l.topBidder != null) {
            l.status = Listing.Status.SOLD;
            CollectionBox.addItems(l.topBidder, l.item, l.left, registries);
            BigInteger net = afterTax(l, l.topBid);
            CollectionBox.addMoney(l.seller, l.currency, net);
            l.left = 0;
            save(l);
            Economy e = Economy.get();
            String amount = currency(l).map(c -> e == null ? c.format(l.topBid).getString() : e.format(c.of(l.topBid))).orElse(l.topBid.toString());
            tell(l.topBidder, "ah.won", "item", itemName, "amount", amount);
            if (Features.on(Features.AH_SALE_ALERTS)) {
                tell(l.seller, "ah.sold", "player", com.tac5studios.elementseconomy.core.Accounts.name(l.topBidder),
                        "count", l.quantity, "item", itemName, "amount", amount);
            }
            TransactionLog.add("AH_SALE", com.tac5studios.elementseconomy.core.Accounts.name(l.topBidder) + " won " + l.quantity + "x "
                    + itemName + " from " + l.sellerName + " for " + amount + " (listing " + l.id + ")");
            if (Features.on(Features.API, Features.API_EVENTS) && l.item != null) {
                UUID winner = l.topBidder;
                currency(l).ifPresent(c -> NeoForge.EVENT_BUS.post(
                        new AuctionSoldEvent(l.seller, winner, l.id, l.item.copyWithCount(l.quantity), c.of(l.topBid))));
            }
            return;
        }
        l.status = l.left < l.quantity ? Listing.Status.SOLD : Listing.Status.EXPIRED;
        if (l.left > 0 && l.item != null) {
            CollectionBox.addItems(l.seller, l.item, l.left, registries);
            tell(l.seller, "ah.expired", "item", itemName);
        }
        l.left = 0;
        save(l);
    }

    /** Return the top bid to its bidder's collection box (outbid, cancelled or removed). */
    static void refundTopBid(Listing l, boolean outbid) {
        if (l.topBidder == null || l.topBid.signum() <= 0) return;
        CollectionBox.addMoney(l.topBidder, l.currency, l.topBid);
        if (outbid) {
            Economy e = Economy.get();
            String amount = currency(l).map(c -> e == null ? c.format(l.topBid).getString() : e.format(c.of(l.topBid))).orElse(l.topBid.toString());
            tell(l.topBidder, "ah.outbid", "item", l.item == null ? "?" : l.item.getHoverName().getString(), "amount", amount);
        }
    }

    static void tell(UUID player, String key, Object... pairs) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
        if (p != null) Msg.send(p, key, pairs);
    }
}
