package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.Menu;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Pricing screen for one row (auction-house sell-screen template, LOCKED):
 * price · bundle size · daily limit (buy shops) or wanted amount (sell shops) · for sale on/off.
 * Prices are set in the server's primary currency.
 */
public final class PricingMenu extends Menu {

    private final Shop shop;
    private final ShopRow row;
    private final OwnerMenu back;

    public PricingMenu(Shop shop, ShopRow row, OwnerMenu back) {
        super(3, row.item.getHoverName());
        this.shop = shop;
        this.row = row;
        this.back = back;
    }

    @Override
    protected boolean stillValid(Player player) {
        return TradeMenu.near(player, shop);
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        Economy e = Economy.get();
        set(4, Button.display(row.item.copy()));

        // Price
        String priceText = Msg.raw("menu.no_price");
        Optional<Currency> cur = ShopTrade.currency(row);
        if (e != null && row.priced() && cur.isPresent()) priceText = e.format(cur.get().of(row.price));
        set(10, Button.of(Icons.named(Items.GOLD_INGOT, Msg.menu(shop.type == ShopType.BUY ? "shop_menu.price" : "shop_menu.offer"),
                List.of(com.tac5studios.elementseconomy.util.Text.color(priceText).withStyle(s -> s.withItalic(false)), Msg.menu("menu.click_change"))), (p, c) ->
                ask(p, Msg.menu("menu.enter_price"), (who, text) -> {
                    if (e == null || e.primaryCurrency() == null) return;
                    Currency pc = e.primaryCurrency();
                    Optional<Money> m = e.parse(pc, text);
                    if (m.isEmpty()) {
                        Msg.send(who, "general.bad_amount", "input", text);
                        return;
                    }
                    row.currency = pc.id().toString();
                    row.price = m.get().amount();
                    if (!row.enabled) row.enabled = true; // pricing a new item puts it on sale
                })));

        // Bundle size
        set(12, Button.of(Icons.named(Items.CHEST, Msg.menu("shop_menu.per", "count", row.per),
                List.of(Msg.menu("shop_menu.per_hint"), Msg.menu("menu.click_change"))), (p, c) ->
                ask(p, Msg.menu("shop_menu.enter_per"), (who, text) -> {
                    Integer n = number(text);
                    if (n == null || n < 1 || n > 6400) {
                        Msg.send(who, "general.bad_amount", "input", text);
                        return;
                    }
                    row.per = n;
                })));

        // Daily limit (buy) or wanted (sell)
        if (shop.type == ShopType.BUY) {
            if (Features.on(Features.PS_DAILY_LIMITS)) {
                set(14, Button.of(Icons.named(Items.CLOCK, Msg.menu("shop_menu.daily", "count", row.dailyLimit == 0 ? "-" : row.dailyLimit),
                        List.of(Msg.menu("shop_menu.daily_hint"), Msg.menu("menu.click_change"))), (p, c) ->
                        ask(p, Msg.menu("shop_menu.enter_daily"), (who, text) -> {
                            Integer n = number(text);
                            if (n == null || n < 0) {
                                Msg.send(who, "general.bad_amount", "input", text);
                                return;
                            }
                            row.dailyLimit = n;
                        })));
            }
        } else {
            set(14, Button.of(Icons.named(Items.HOPPER, Msg.menu("menu.wanted", "count", row.wanted),
                    List.of(Msg.menu("shop_menu.wanted_hint"), Msg.menu("menu.click_change"))), (p, c) ->
                    ask(p, Msg.menu("shop_menu.enter_wanted"), (who, text) -> {
                        Integer n = number(text);
                        if (n == null || n < 0) {
                            Msg.send(who, "general.bad_amount", "input", text);
                            return;
                        }
                        row.wanted = n;
                    })));
        }

        // On/off
        set(16, Button.of(Icons.named(row.enabled ? Items.LIME_DYE : Items.GRAY_DYE,
                Msg.menu(row.enabled ? "shop_menu.on" : "shop_menu.off")), (p, c) -> {
            if (!row.priced()) {
                Msg.send(p, "shop.no_price", "item", row.item.getHoverName().getString(), "shop", shop.name);
                return;
            }
            row.enabled = !row.enabled;
            Shops.save(shop);
            refresh();
        }));

        // Back, and remove (sell shops: rows are the owner's list)
        set(22, Button.of(Icons.named(Items.ARROW, Msg.menu("shop_menu.back")), (p, c) -> back.open(p)));
        if (shop.type == ShopType.SELL) {
            set(26, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("shop_menu.remove_row")), (p, c) -> {
                shop.rows.remove(row.key);
                Shops.save(shop);
                back.open(p);
            }));
        }
    }

    private void ask(ServerPlayer p, Component title, java.util.function.BiConsumer<ServerPlayer, String> apply) {
        TextInput.ask(p, title, "", (who, text) -> {
            apply.accept(who, text.trim());
            Shops.save(shop);
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
