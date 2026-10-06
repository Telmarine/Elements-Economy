package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.ConfirmMenu;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementseconomy.ui.PriceIcons;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The customer screen of a player shop (LOCKED layout, UI.md).
 * Columns: 1 stock/wanted · 2 item · 3 filler · 4-5 price · 6 filler · 7 amount · 8 Buy/Sell · 9 filler.
 */
public final class TradeMenu extends PagedMenu<ShopRow> {

    private static final int[] AMOUNTS = {1, 8, 16, 64};

    private final Shop shop;
    private final Map<String, Integer> chosen = new HashMap<>();
    private String search = "";
    private int sort; // 0 name, 1 price low-high, 2 price high-low

    public TradeMenu(Shop shop) {
        super(Component.literal(shop.name), Layout.ROWS);
        this.shop = shop;
    }

    private boolean buy() {
        return shop.type == ShopType.BUY;
    }

    @Override
    protected boolean stillValid(Player player) {
        return near(player, shop);
    }

    static boolean near(Player player, Shop shop) {
        int d = ShopConfig.USE_DISTANCE.get();
        return player.level().dimension().equals(shop.dimension)
                && player.blockPosition().distSqr(shop.pos()) <= (double) d * d
                && Shops.get(shop.id) != null;
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("shop_menu.empty");
    }

    @Override
    protected List<ShopRow> entries() {
        List<ShopRow> out = new ArrayList<>();
        for (ShopRow r : shop.rows.values()) {
            if (!r.listed()) continue;
            if (!search.isEmpty() && !r.item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)) continue;
            out.add(r);
        }
        Comparator<ShopRow> byName = Comparator.comparing(r -> r.item.getHoverName().getString());
        switch (sort) {
            case 1 -> out.sort(Comparator.comparing((ShopRow r) -> r.price).thenComparing(byName));
            case 2 -> out.sort(Comparator.comparing((ShopRow r) -> r.price).reversed().thenComparing(byName));
            default -> out.sort(byName);
        }
        return out;
    }

    // ---------- one row ----------

    /** Most items this player can trade on this row right now. */
    private int maxItems(ShopRow r) {
        ServerLevel level = ShopTrade.level(viewer, shop);
        if (level == null) return 0;
        if (buy()) {
            int n = ShopStock.available(level, shop, r);
            if (r.dailyLimit > 0 && Features.on(Features.PS_DAILY_LIMITS)) {
                n = Math.min(n, Math.max(0, r.dailyLimit - ShopDaily.used(shop, r, viewer.getUUID())));
            }
            return n;
        }
        int carry = ShopStock.count(ShopTrade.inventory(viewer), r.item);
        return Math.min(carry, canBuyMore(r));
    }

    /** SELL: how many more the shop takes (wanted, till and room). Never shown as money. */
    private int canBuyMore(ShopRow r) {
        ServerLevel level = ShopTrade.level(viewer, shop);
        Optional<Currency> cur = ShopTrade.currency(r);
        if (level == null || cur.isEmpty() || r.price.signum() <= 0) return 0;
        BigInteger affordable = shop.till(cur.get().id().toString()).divide(r.price).multiply(BigInteger.valueOf(r.per));
        int n = Math.min(r.wanted, affordable.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue());
        return Math.min(n, ShopStock.roomForSold(level, shop, r, n));
    }

    /** Chosen bundles, capped by what's possible (0 = none possible). */
    private int bundles(ShopRow r) {
        int max = maxItems(r) / r.per;
        if (max <= 0) return 0;
        int i = chosen.getOrDefault(r.key, 0);
        return Math.min(AMOUNTS[Math.max(0, Math.min(i, AMOUNTS.length - 1))], max);
    }

    @Override
    protected void drawEntry(ShopRow r, int row) {
        Economy e = Economy.get();
        Optional<Currency> cur = ShopTrade.currency(r);
        if (e == null || cur.isEmpty()) return;
        boolean tips = Features.on(Features.UI_TOOLTIPS);
        String owner = shop.ownerName;
        ServerLevel level = ShopTrade.level(viewer, shop);
        int bundles = bundles(r);
        int items = bundles * r.per;
        Money each = cur.get().of(r.price);
        Money total = each.multiply(Math.max(bundles, 1));
        Money balance = e.balance(viewer.getUUID(), cur.get());

        // 1: stock / wanted
        List<Component> t1 = new ArrayList<>();
        ItemStack stock;
        if (buy()) {
            int avail = level == null ? 0 : ShopStock.available(level, shop, r);
            stock = Icons.named(Items.PAPER, Msg.menu("menu.stock", "count", avail >= 64 ? "64+" : String.valueOf(avail)));
            stock.setCount(Math.max(1, Math.min(64, avail)));
            if (tips && shop.vault != null && Features.on(Features.STOCK_VAULTS)) t1.add(Msg.menu("menu.restocks"));
        } else {
            int more = canBuyMore(r);
            stock = more > 0
                    ? Icons.named(Items.HOPPER, Msg.menu("menu.wanted", "count", more))
                    : Icons.named(Items.BARRIER, Msg.menu("shop_menu.not_buying"));
        }
        if (tips) Icons.addLore(stock, t1);
        set(row, 0, Button.display(stock));

        // 2: the item itself (its own tooltip + shop info)
        ItemStack item = r.item.copyWithCount(Math.max(1, Math.min(r.per, r.item.getMaxStackSize())));
        if (tips) {
            List<Component> t2 = new ArrayList<>();
            t2.add(Msg.menu("menu.owner", "shop", shop.name, "owner", owner));
            if (r.per > 1) t2.add(Msg.menu("shop_menu.per", "count", r.per));
            if (!buy()) t2.add(Msg.menu("menu.you_carry", "count", ShopStock.count(ShopTrade.inventory(viewer), r.item)));
            Icons.addLore(item, t2);
        }
        set(row, 1, Button.display(item));
        set(row, 2, Button.display(Icons.filler()));

        // 4-5: price per bundle
        List<ItemStack> price = PriceIcons.slots(each, 2);
        for (int i = 0; i < 2; i++) {
            ItemStack p = i < price.size() ? price.get(i) : Icons.filler();
            if (tips && i < price.size()) {
                List<Component> tp = new ArrayList<>();
                tp.add(Msg.menu("menu.price_each", "amount", e.format(each)));
                int stack = r.item.getMaxStackSize();
                if (stack > 1 && stack >= r.per) {
                    tp.add(Msg.menu("menu.price_stack", "amount", e.format(each.multiply(stack / r.per))));
                }
                Icons.addLore(p, tp);
            }
            set(row, 3 + i, Button.display(p));
        }
        set(row, 5, Button.display(Icons.filler()));

        // 7: amount
        ItemStack amount = Icons.named(Items.CHEST, Msg.menu("menu.amount", "count", Math.max(items, r.per)));
        amount.setCount(Math.max(1, Math.min(64, Math.max(bundles, 1))));
        if (tips) {
            Icons.addLore(amount, List.of(
                    Msg.menu(buy() ? "shop_menu.cost" : "shop_menu.you_get", "amount", e.format(total)),
                    Msg.menu("menu.click_change")));
        }
        set(row, 6, Button.of(amount, (p, c) -> {
            int i = chosen.getOrDefault(r.key, 0);
            i = c.isRight() ? (i + AMOUNTS.length - 1) % AMOUNTS.length : (i + 1) % AMOUNTS.length;
            chosen.put(r.key, i);
            refresh();
        }));

        // 8: Buy / Sell
        boolean possible = bundles > 0;
        ItemStack act;
        List<Component> t8 = new ArrayList<>();
        if (buy()) {
            act = Icons.named(possible ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE, Msg.menu("menu.buy"));
            t8.add(Msg.menu("shop_menu.buy_line", "count", items, "item", r.item.getHoverName().getString()));
            t8.add(Msg.menu("shop_menu.total", "amount", e.format(total)));
            t8.add(Msg.menu("menu.balance_now", "balance", e.format(balance)));
            if (balance.compareTo(total) >= 0) t8.add(Msg.menu("menu.balance_after", "balance", e.format(balance.subtract(total))));
            else t8.add(Msg.menu("menu.not_enough"));
        } else {
            act = Icons.named(possible ? Items.GOLD_BLOCK : Items.GRAY_CONCRETE, Msg.menu("menu.sell"));
            t8.add(Msg.menu("shop_menu.sell_line", "count", items, "item", r.item.getHoverName().getString()));
            t8.add(Msg.menu("shop_menu.you_get", "amount", e.format(total)));
            t8.add(Msg.menu("menu.balance_now", "balance", e.format(balance)));
            t8.add(Msg.menu("menu.balance_after", "balance", e.format(balance.add(total))));
        }
        t8.add(Msg.menu("menu.click_confirm"));
        if (tips) Icons.addLore(act, t8);
        set(row, 7, Button.of(act, (p, c) -> {
            if (!possible) return;
            ItemStack preview = r.item.copyWithCount(Math.max(1, Math.min(64, items)));
            ConfirmMenu.ask(p, preview, t8.subList(0, t8.size() - 1), who -> {
                if (buy()) ShopTrade.buy(who, shop, r, bundles);
                else ShopTrade.sell(who, shop, r, bundles);
                open(who);
            }, this::open);
        }));
        set(row, 8, Button.display(Icons.filler()));
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(Icons.named(Items.BARRIER, Msg.menu("menu.close")), (p, c) -> close()));
        if (Features.on(Features.UI_SORT)) {
            String[] names = {"A-Z", "Price ↑", "Price ↓"};
            navButton(3, Button.of(Icons.named(Items.HOPPER, Msg.menu("menu.sort", "sort", names[sort])), (p, c) -> {
                sort = (sort + 1) % 3;
                page = 0;
                refresh();
            }));
        }
        if (Features.on(Features.UI_SEARCH)) {
            if (search.isEmpty()) {
                navButton(4, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("menu.search")), (p, c) ->
                        TextInput.ask(p, Msg.menu("menu.enter_search"), "", (who, text) -> {
                            search = text.toLowerCase(Locale.ROOT);
                            page = 0;
                            open(who);
                        }, this::open)));
            } else {
                navButton(4, Button.of(Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("shop_menu.clear_search", "text", search)), (p, c) -> {
                    search = "";
                    refresh();
                }));
            }
        }
        if (Features.on(Features.UI_BALANCE_BUTTON)) {
            Economy e = Economy.get();
            if (e != null && e.primaryCurrency() != null) {
                Currency cur = e.primaryCurrency();
                navButton(5, Button.display(Icons.named(cur.icon().getItem(),
                        Msg.menu("menu.balance", "balance", e.format(e.balance(viewer.getUUID(), cur))))));
            }
        }
    }
}
