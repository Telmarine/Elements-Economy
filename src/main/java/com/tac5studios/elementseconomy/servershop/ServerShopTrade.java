package com.tac5studios.elementseconomy.servershop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.integration.NexusHook;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.shop.ShopStock;
import com.tac5studios.elementseconomy.shop.ShopTrade;
import com.tac5studios.elementseconomy.shop.ShopType;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.event.ShopPurchaseEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Trading with server shops. Buy shops take the player's money (it leaves the economy);
 * sell shops pay the player (new money). Stock limits and rank prices apply when switched on.
 */
public final class ServerShopTrade {

    private ServerShopTrade() {}

    public static Optional<Currency> currency(ServerRow r) {
        Economy e = Economy.get();
        ResourceLocation id = ResourceLocation.tryParse(r.currency);
        return e == null || id == null ? Optional.empty() : e.currency(id);
    }

    /** Price per bundle for this player: their rank's price when rank prices are on, else the normal price. */
    public static BigInteger price(ServerRow r, UUID player) {
        if (Features.on(Features.SERVER_SHOPS, Features.SS_RANK_PRICES) && !r.rankPrices.isEmpty()) {
            Optional<String> rank = NexusHook.rank(player);
            if (rank.isPresent()) {
                for (Map.Entry<String, BigInteger> e : r.rankPrices.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(rank.get())) return e.getValue();
                }
            }
        }
        return r.price;
    }

    public static boolean rankPriced(ServerRow r, UUID player) {
        return !price(r, player).equals(r.price);
    }

    /** Items left to trade on this row, or Integer.MAX_VALUE when unlimited. */
    public static int left(ServerRow r) {
        return Features.on(Features.SERVER_SHOPS, Features.SS_STOCK_LIMITS) && r.limited() ? Math.max(0, r.stock) : Integer.MAX_VALUE;
    }

    public static boolean trade(ServerPlayer p, ServerShop s, ServerRow r, int bundles) {
        Economy e = Economy.get();
        Optional<Currency> cur = currency(r);
        if (!s.open) {
            Msg.send(p, "shop.closed", "shop", s.name);
            return false;
        }
        if (e == null || cur.isEmpty() || !r.listed() || bundles <= 0) {
            Msg.send(p, "general.currency_unavailable");
            return false;
        }
        String itemName = r.item.getHoverName().getString();
        int items = bundles * r.per;
        if (left(r) < items) {
            Msg.send(p, "servershop.stock_limit", "item", itemName);
            return false;
        }
        Money total = cur.get().of(price(r, p.getUUID()).multiply(BigInteger.valueOf(bundles)));
        IItemHandler inv = ShopTrade.inventory(p);
        Cause cause = Cause.shop("server:" + s.id, p.getUUID());

        if (s.type == ShopType.BUY) {
            Money have = e.balance(p.getUUID(), cur.get());
            if (have.compareTo(total) < 0) {
                Msg.send(p, "shop.not_enough", "amount", e.format(total), "balance", e.format(have));
                return false;
            }
            if (ShopStock.room(inv, r.item, items) < items) {
                Msg.send(p, "shop.no_room");
                return false;
            }
            Result paid = e.withdraw(p.getUUID(), total, cause);
            if (!paid.success()) {
                Msg.send(p, "shop.not_enough", "amount", e.format(total), "balance", e.format(have));
                return false;
            }
            int left = items;
            while (left > 0) {
                int n = Math.min(left, r.item.getMaxStackSize());
                ItemStack st = r.item.copyWithCount(n);
                if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
                left -= n;
            }
            Msg.send(p, "shop.bought", "count", items, "item", itemName, "amount", e.format(total));
        } else {
            if (ShopStock.count(inv, r.item) < items) {
                Msg.send(p, "general.bad_amount", "input", items);
                return false;
            }
            if (!e.canDeposit(p.getUUID(), total).success()) {
                Msg.send(p, "general.currency_unavailable");
                return false;
            }
            List<ItemStack> taken = ShopStock.extract(inv, r.item, items);
            Result paid = e.deposit(p.getUUID(), total, cause);
            if (!paid.success()) {
                for (ItemStack st : taken) if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
                Msg.send(p, "general.currency_unavailable");
                return false;
            }
            Msg.send(p, "shop.sold", "count", items, "item", itemName, "amount", e.format(total));
        }
        p.containerMenu.broadcastChanges();

        if (left(r) != Integer.MAX_VALUE) {
            r.stock -= items;
            ServerShops.save(s);
        }
        if (Features.on(Features.API, Features.API_EVENTS)) {
            NeoForge.EVENT_BUS.post(new ShopPurchaseEvent(p.getUUID(), null, "server:" + s.id, r.item, items, total, s.type == ShopType.BUY));
        }
        return true;
    }
}
