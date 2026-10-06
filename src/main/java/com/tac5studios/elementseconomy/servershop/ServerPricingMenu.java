package com.tac5studios.elementseconomy.servershop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.shop.ShopType;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.Menu;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Staff pricing screen for one server shop row:
 * price · bundle size · stock limit + refill (stock limits on) · rank prices (rank prices on) · on/off · remove.
 */
public final class ServerPricingMenu extends Menu {

    private final ServerShop shop;
    private final ServerRow row;
    private final ServerEditMenu back;

    public ServerPricingMenu(ServerShop shop, ServerRow row, ServerEditMenu back) {
        super(3, row.item.getHoverName());
        this.shop = shop;
        this.row = row;
        this.back = back;
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        Economy e = Economy.get();
        set(4, Button.display(row.item.copy()));

        String priceText = Msg.raw("menu.no_price");
        Optional<Currency> cur = ServerShopTrade.currency(row);
        if (e != null && row.priced() && cur.isPresent()) priceText = e.format(cur.get().of(row.price));
        set(10, Button.of(Icons.named(Items.GOLD_INGOT, Msg.menu(shop.type == ShopType.BUY ? "shop_menu.price" : "shop_menu.offer"),
                List.of(com.tac5studios.elementseconomy.util.Text.color(priceText).withStyle(s -> s.withItalic(false)), Msg.menu("menu.click_change"))), (p, c) ->
                ask(p, Msg.menu("menu.enter_price"), (who, text) -> {
                    Optional<Money> m = parse(text);
                    if (m.isEmpty()) {
                        Msg.send(who, "general.bad_amount", "input", text);
                        return;
                    }
                    row.currency = m.get().currency().id().toString();
                    row.price = m.get().amount();
                    row.enabled = true;
                })));

        set(11, Button.of(Icons.named(Items.CHEST, Msg.menu("shop_menu.per", "count", row.per),
                List.of(Msg.menu("shop_menu.per_hint"), Msg.menu("menu.click_change"))), (p, c) ->
                ask(p, Msg.menu("shop_menu.enter_per"), (who, text) -> {
                    Integer n = number(text);
                    if (n == null || n < 1 || n > 6400) {
                        Msg.send(who, "general.bad_amount", "input", text);
                        return;
                    }
                    row.per = n;
                })));

        if (Features.on(Features.SS_STOCK_LIMITS)) {
            List<Component> lore = new ArrayList<>();
            lore.add(row.limited() ? Msg.menu("servershop.menu_stock", "count", row.stock, "max", row.stockMax) : Msg.menu("menu.unlimited"));
            lore.add(Msg.menu("servershop.menu_stock_hint"));
            lore.add(Msg.menu("menu.click_change"));
            set(12, Button.of(Icons.named(Items.BARREL, Msg.menu("servershop.menu_stock_limit"), lore), (p, c) ->
                    ask(p, Msg.menu("servershop.enter_stock"), (who, text) -> {
                        Integer n = number(text);
                        if (n == null || n < 0) {
                            Msg.send(who, "general.bad_amount", "input", text);
                            return;
                        }
                        row.stockMax = n;
                        row.stock = n;
                        row.lastRefill = System.currentTimeMillis();
                    })));
            set(13, Button.of(Icons.named(Items.CLOCK, Msg.menu("servershop.menu_refill", "count", row.refillAmount, "minutes", row.refillMinutes),
                    List.of(Msg.menu("servershop.menu_refill_hint"), Msg.menu("menu.click_change"))), (p, c) ->
                    ask(p, Msg.menu("servershop.enter_refill"), (who, text) -> {
                        String[] parts = text.trim().split("\\s+");
                        Integer amount = parts.length == 2 ? number(parts[0]) : null;
                        Integer minutes = parts.length == 2 ? number(parts[1]) : null;
                        if (amount == null || minutes == null || amount < 0 || minutes < 0) {
                            Msg.send(who, "general.bad_amount", "input", text);
                            return;
                        }
                        row.refillAmount = amount;
                        row.refillMinutes = minutes;
                    })));
        }

        if (Features.on(Features.SS_RANK_PRICES)) {
            List<Component> lore = new ArrayList<>();
            if (e != null && cur.isPresent()) {
                for (Map.Entry<String, BigInteger> rp : row.rankPrices.entrySet()) {
                    lore.add(Component.literal(rp.getKey() + ": " + e.format(cur.get().of(rp.getValue()))));
                }
            }
            lore.add(Msg.menu("servershop.menu_rank_hint"));
            lore.add(Msg.menu("menu.click_change"));
            set(14, Button.of(Icons.named(Items.EMERALD, Msg.menu("servershop.menu_rank_title"), lore), (p, c) ->
                    ask(p, Msg.menu("servershop.enter_rank"), (who, text) -> {
                        String t = text.trim();
                        int sp = t.lastIndexOf(' ');
                        if (sp <= 0) {
                            Msg.send(who, "general.bad_amount", "input", text);
                            return;
                        }
                        String rank = t.substring(0, sp).trim().toLowerCase();
                        String amount = t.substring(sp + 1).trim();
                        if (amount.equals("-")) {
                            row.rankPrices.remove(rank);
                            return;
                        }
                        Optional<Money> m = parse(amount);
                        if (m.isEmpty() || !m.get().currency().id().toString().equals(row.currency)) {
                            Msg.send(who, "general.bad_amount", "input", text);
                            return;
                        }
                        row.rankPrices.put(rank, m.get().amount());
                    })));
        }

        set(16, Button.of(Icons.named(row.enabled ? Items.LIME_DYE : Items.GRAY_DYE, Msg.menu(row.enabled ? "shop_menu.on" : "shop_menu.off")), (p, c) -> {
            if (!row.priced()) {
                Msg.send(p, "shop.no_price", "item", row.item.getHoverName().getString(), "shop", shop.name);
                return;
            }
            row.enabled = !row.enabled;
            ServerShops.save(shop);
            refresh();
        }));
        set(22, Button.of(Icons.named(Items.ARROW, Msg.menu("shop_menu.back")), (p, c) -> back.open(p)));
        set(26, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("servershop.menu_remove")), (p, c) -> {
            shop.rows.remove(row.key);
            ServerShops.save(shop);
            back.open(p);
        }));
    }

    /** Prices are in the primary currency. */
    private static Optional<Money> parse(String text) {
        Economy e = Economy.get();
        if (e == null || e.primaryCurrency() == null) return Optional.empty();
        return e.parse(e.primaryCurrency(), text.trim());
    }

    private void ask(ServerPlayer p, Component title, BiConsumer<ServerPlayer, String> apply) {
        TextInput.ask(p, title, "", (who, text) -> {
            apply.accept(who, text);
            ServerShops.save(shop);
            open(who);
        }, this::open);
    }

    private static Integer number(String text) {
        try {
            return Integer.parseInt(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
