package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.event.ShopPurchaseEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Buying from and selling to player shops. Every step is checked before anything moves,
 * and anything that fails half-way is put back (money refunded, items returned).
 */
public final class ShopTrade {

    private ShopTrade() {}

    public static Optional<Currency> currency(ShopRow r) {
        Economy e = Economy.get();
        ResourceLocation id = ResourceLocation.tryParse(r.currency == null ? "" : r.currency);
        return e == null || id == null ? Optional.empty() : e.currency(id);
    }

    public static ServerLevel level(ServerPlayer p, Shop s) {
        return p.server.getLevel(s.dimension);
    }

    public static IItemHandler inventory(ServerPlayer p) {
        return new PlayerMainInvWrapper(p.getInventory());
    }

    /** Player buys {@code bundles} bundles from a BUY shop. */
    public static boolean buy(ServerPlayer p, Shop s, ShopRow r, int bundles) {
        if (!s.open) {
            Msg.send(p, "shop.closed", "shop", s.name);
            return false;
        }
        Economy e = Economy.get();
        ServerLevel level = level(p, s);
        Optional<Currency> cur = currency(r);
        if (e == null || level == null || cur.isEmpty() || !r.listed() || bundles <= 0) {
            Msg.send(p, "general.currency_unavailable");
            return false;
        }
        String itemName = r.item.getHoverName().getString();
        int items = bundles * r.per;
        if (ShopStock.available(level, s, r) < items) {
            Msg.send(p, "shop.out_of_stock", "item", itemName);
            return false;
        }
        if (r.dailyLimit > 0 && Features.on(Features.PS_DAILY_LIMITS) && ShopDaily.used(s, r, p.getUUID()) + items > r.dailyLimit) {
            Msg.send(p, "shop.daily_limit", "item", itemName);
            return false;
        }
        Money cost = cur.get().of(r.price.multiply(BigInteger.valueOf(bundles)));
        Money have = e.balance(p.getUUID(), cur.get());
        if (have.compareTo(cost) < 0) {
            Msg.send(p, "shop.not_enough", "amount", e.format(cost), "balance", e.format(have));
            return false;
        }
        if (ShopStock.room(inventory(p), r.item, items) < items) {
            Msg.send(p, "shop.no_room");
            return false;
        }

        Result paid = e.withdraw(p.getUUID(), cost, Cause.shop(s.id, p.getUUID()));
        if (!paid.success()) {
            Msg.send(p, "shop.not_enough", "amount", e.format(cost), "balance", e.format(have));
            return false;
        }
        List<ItemStack> got = ShopStock.takeForSale(level, s, r, items);
        int count = got.stream().mapToInt(ItemStack::getCount).sum();
        if (count < items) {
            // Stock changed under us: put it back and refund.
            for (ItemStack st : got) ShopStock.insert(ShopStock.shop(level, s), st, false);
            e.deposit(p.getUUID(), cost, Cause.system("refund: shop " + s.id));
            Msg.send(p, "shop.out_of_stock", "item", itemName);
            return false;
        }
        for (ItemStack st : got) {
            if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        }
        p.containerMenu.broadcastChanges();

        s.addTill(cur.get().id().toString(), cost.amount());
        Shops.save(s);
        if (r.dailyLimit > 0) ShopDaily.add(s, r, p.getUUID(), items);

        Msg.send(p, "shop.bought", "count", items, "item", itemName, "amount", e.format(cost));
        alert(p, s, "shop.alert_sold", items, itemName, e.format(cost));
        if (Features.on(Features.API, Features.API_EVENTS)) {
            NeoForge.EVENT_BUS.post(new ShopPurchaseEvent(p.getUUID(), s.owner, s.id, r.item, items, cost, true));
        }

        if (s.vault != null && Features.on(Features.STOCK_VAULTS, Features.SV_RESTOCK_AFTER_SALE)) {
            StockVaults.restock(p.server, s);
        }
        if (ShopStock.available(level, s, r) == 0) {
            ServerPlayer owner = p.server.getPlayerList().getPlayer(s.owner);
            if (owner != null && Features.on(Features.PS_SALE_ALERTS)) Msg.send(owner, "shop.alert_empty", "shop", s.name, "item", itemName);
        }
        return true;
    }

    /** Player sells {@code bundles} bundles to a SELL shop. */
    public static boolean sell(ServerPlayer p, Shop s, ShopRow r, int bundles) {
        if (!s.open) {
            Msg.send(p, "shop.closed", "shop", s.name);
            return false;
        }
        Economy e = Economy.get();
        ServerLevel level = level(p, s);
        Optional<Currency> cur = currency(r);
        if (e == null || level == null || cur.isEmpty() || !r.listed() || bundles <= 0) {
            Msg.send(p, "general.currency_unavailable");
            return false;
        }
        String itemName = r.item.getHoverName().getString();
        int items = bundles * r.per;
        Money offer = cur.get().of(r.price.multiply(BigInteger.valueOf(bundles)));
        IItemHandler inv = inventory(p);

        if (r.wanted < items || s.till(cur.get().id().toString()).compareTo(offer.amount()) < 0
                || ShopStock.roomForSold(level, s, r, items) < items) {
            Msg.send(p, "shop.not_buying");
            return false;
        }
        if (ShopStock.count(inv, r.item) < items) {
            Msg.send(p, "general.bad_amount", "input", items);
            return false;
        }
        if (!e.canDeposit(p.getUUID(), offer).success()) {
            Msg.send(p, "general.currency_unavailable");
            return false;
        }

        List<ItemStack> taken = ShopStock.extract(inv, r.item, items);
        for (ItemStack st : taken) {
            ItemStack left = ShopStock.storeSold(level, s, st);
            if (!left.isEmpty() && !p.getInventory().add(left)) p.drop(left, false);
        }
        Result paid = e.deposit(p.getUUID(), offer, Cause.shop(s.id, p.getUUID()));
        if (!paid.success()) {
            // Give the items back.
            for (ItemStack st : ShopStock.takeBackSold(level, s, r, items)) {
                if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
            }
            Msg.send(p, "general.currency_unavailable");
            return false;
        }
        p.containerMenu.broadcastChanges();

        s.addTill(cur.get().id().toString(), offer.amount().negate());
        r.wanted -= items;
        Shops.save(s);
        if (s.vault != null && Features.on(Features.STOCK_VAULTS, Features.SV_RESTOCK_AFTER_SALE)) {
            ShopStock.moveShopToVault(level, s, r);
        }

        Msg.send(p, "shop.sold", "count", items, "item", itemName, "amount", e.format(offer));
        alert(p, s, "shop.alert_bought", items, itemName, e.format(offer));
        if (Features.on(Features.API, Features.API_EVENTS)) {
            NeoForge.EVENT_BUS.post(new ShopPurchaseEvent(p.getUUID(), s.owner, s.id, r.item, items, offer, false));
        }
        return true;
    }

    private static void alert(ServerPlayer customer, Shop s, String key, int items, String item, String amount) {
        if (!Features.on(Features.PS_SALE_ALERTS) || customer.getUUID().equals(s.owner)) return;
        ServerPlayer owner = customer.server.getPlayerList().getPlayer(s.owner);
        if (owner != null) {
            Msg.send(owner, key, "player", customer.getGameProfile().getName(), "count", items, "item", item,
                    "shop", s.name, "amount", amount);
        }
    }
}
