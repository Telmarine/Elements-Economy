package com.tac5studios.elementseconomy.servershop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.shop.ShopStock;
import com.tac5studios.elementseconomy.shop.ShopType;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementseconomy.ui.PriceIcons;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Staff editor for a server shop (auction-house template, like the player owner screen).
 * Shift-click an item in your inventory (or hold it and click the hopper) to add it; click a row to set its price, bundle size,
 * stock limit and rank prices.
 */
public final class ServerEditMenu extends PagedMenu<ServerRow> {

    private final ServerShop shop;

    public ServerEditMenu(ServerShop shop) {
        super(Component.literal(shop.name).append(" ✎"), Layout.ROWS);
        this.shop = shop;
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("servershop.menu_empty");
    }

    @Override
    protected List<ServerRow> entries() {
        List<ServerRow> out = new ArrayList<>();
        for (ServerRow r : shop.rows.values()) if (r.item != null) out.add(r);
        return out;
    }

    @Override
    protected void drawEntry(ServerRow r, int row) {
        Economy e = Economy.get();
        Optional<Currency> cur = ServerShopTrade.currency(r);

        List<Component> info = new ArrayList<>();
        if (r.limited() && Features.on(Features.SS_STOCK_LIMITS)) {
            info.add(Msg.menu("servershop.menu_stock", "count", r.stock, "max", r.stockMax));
            if (r.refillMinutes > 0) info.add(Msg.menu("servershop.menu_refill", "count", r.refillAmount, "minutes", r.refillMinutes));
        } else {
            info.add(Msg.menu("menu.unlimited"));
        }
        if (!r.rankPrices.isEmpty() && Features.on(Features.SS_RANK_PRICES)) {
            info.add(Msg.menu("servershop.menu_rank_prices", "count", r.rankPrices.size()));
        }
        set(row, 0, Button.display(Icons.named(Items.PAPER, Msg.menu(shop.type == ShopType.BUY ? "servershop.menu_selling" : "servershop.menu_buying"), info)));
        set(row, 1, Button.display(r.item.copy()));
        set(row, 2, Button.display(Icons.filler()));
        if (r.priced() && cur.isPresent() && e != null) {
            List<ItemStack> price = PriceIcons.slots(cur.get().of(r.price), 2);
            for (int i = 0; i < 2; i++) set(row, 3 + i, Button.display(i < price.size() ? price.get(i) : Icons.filler()));
        } else {
            set(row, 3, Button.display(Icons.named(Items.BARRIER, Msg.menu("menu.no_price"))));
            set(row, 4, Button.display(Icons.filler()));
        }
        set(row, 5, Button.display(Icons.filler()));
        set(row, 6, Button.display(Icons.named(Items.CHEST, Msg.menu("shop_menu.per", "count", r.per))));
        set(row, 7, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("shop_menu.edit"), List.of(Msg.menu("menu.click_change"))),
                (p, c) -> new ServerPricingMenu(shop, r, this).open(p)));
        boolean on = r.enabled;
        set(row, 8, Button.of(Icons.named(on ? Items.LIME_DYE : Items.GRAY_DYE, Msg.menu(on ? "shop_menu.on" : "shop_menu.off")), (p, c) -> {
            if (!r.priced()) {
                Msg.send(p, "shop.no_price", "item", r.item.getHoverName().getString(), "shop", shop.name);
                return;
            }
            r.enabled = !r.enabled;
            ServerShops.save(shop);
            refresh();
        }));
    }

    @Override
    protected void drawNav() {
        boolean open = shop.open;
        navButton(0, Button.of(Icons.named(open ? Items.LIME_BANNER : Items.RED_BANNER,
                Msg.menu(open ? "shop_menu.shop_open" : "shop_menu.shop_closed"), List.of(Msg.menu("menu.click_change"))), (p, c) -> {
            shop.open = !shop.open;
            ServerShops.save(shop);
            refresh();
        }));
        navButton(1, Button.of(Icons.named(Items.HOPPER, Msg.menu("servershop.menu_add"),
                        List.of(Msg.menu("servershop.menu_add_shift"), Msg.menu("shop_menu.add_item_hint"))),
                (p, c) -> addFromHand(p)));
        navButton(2, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("shop_menu.rename")), (p, c) ->
                TextInput.ask(p, Msg.menu("menu.enter_name"), shop.name, (who, text) -> {
                    String t = text.trim();
                    if (!t.isEmpty() && t.length() <= 32) {
                        shop.name = t;
                        ServerShops.save(shop);
                        setTitle(Component.literal(t).append(" ✎"));
                    }
                    open(who);
                }, this::open)));
        navButton(3, Button.display(Icons.named(Items.ENDER_EYE, Msg.menu("servershop.menu_links", "count", shop.links.size()),
                List.of(Msg.menu("servershop.menu_links_hint")))));
    }

    @Override
    protected boolean onInventoryShiftClick(ServerPlayer p, ItemStack stack) {
        add(stack);
        return true;
    }

    private void addFromHand(ServerPlayer p) {
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) {
            Msg.send(p, "servershop.add_how");
            return;
        }
        add(hand);
    }

    /** Add an item type to the shop (a copy; the player keeps theirs). */
    private void add(ItemStack hand) {
        String key = ShopStock.key(hand);
        if (!shop.rows.containsKey(key)) {
            ServerRow r = new ServerRow(key);
            r.item = hand.copyWithCount(1);
            shop.rows.put(key, r);
            ServerShops.save(shop);
        }
        refresh();
    }
}
