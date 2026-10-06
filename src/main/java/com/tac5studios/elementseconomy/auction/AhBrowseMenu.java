package com.tac5studios.elementseconomy.auction;

import com.mojang.authlib.GameProfile;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.ConfirmMenu;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementseconomy.ui.PriceIcons;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Auction house browse screen (LOCKED layout, UI.md), 5 listings per page:
 * 1 seller · 2 item · 3 filler · 4-5 price · 6 filler · 7 amount (static) · 8 Buy/Bid · 9 time left.
 * Nav: close · category · prev · page · next · sort · search · my listings · collection box.
 */
public final class AhBrowseMenu extends PagedMenu<Listing> {

    private static final String[] CATEGORIES = {"all", "blocks", "tools", "armor", "food", "other"};
    private static final String[] SORTS = {"newest", "ending", "price_up", "price_down"};

    private int category;
    private int sort;
    private String search = "";

    public AhBrowseMenu() {
        super(Msg.menu("menu.ah_title"), Layout.ROWS);
    }

    static String category(ItemStack s) {
        if (s.getItem() instanceof ArmorItem) return "armor";
        if (s.has(DataComponents.FOOD)) return "food";
        if (s.isDamageableItem()) return "tools";
        if (s.getItem() instanceof BlockItem) return "blocks";
        return "other";
    }

    /** Price per item for sorting (auctions: current or starting bid per item). */
    private static java.math.BigInteger perItem(Listing l) {
        java.math.BigInteger p = l.auction ? (l.bids > 0 ? l.topBid : l.price) : l.price;
        return l.auction ? p.divide(java.math.BigInteger.valueOf(Math.max(1, l.left))) : p;
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("ah_menu.empty");
    }

    @Override
    protected List<Listing> entries() {
        List<Listing> out = new ArrayList<>();
        String cat = CATEGORIES[category];
        for (Listing l : Auctions.active()) {
            if (!Features.on(Features.AH_BIDS) && l.auction) continue;
            if (!Features.on(Features.AH_BUY_NOW) && !l.auction) continue;
            if (!"all".equals(cat) && !cat.equals(category(l.item))) continue;
            if (!search.isEmpty() && !l.item.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)
                    && !l.sellerName.toLowerCase(Locale.ROOT).contains(search)) continue;
            out.add(l);
        }
        switch (SORTS[sort]) {
            case "ending" -> out.sort(Comparator.comparingLong(l -> l.ends));
            case "price_up" -> out.sort(Comparator.comparing(AhBrowseMenu::perItem));
            case "price_down" -> out.sort(Comparator.comparing(AhBrowseMenu::perItem).reversed());
            default -> out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
        }
        return out;
    }

    static String timeLeft(long ends) {
        long s = Math.max(0, (ends - System.currentTimeMillis()) / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return Math.max(1, m) + "m";
    }

    static ItemStack head(Listing l) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(new GameProfile(l.seller, l.sellerName)));
        return head;
    }

    @Override
    protected void drawEntry(Listing l, int row) {
        Economy e = Economy.get();
        Optional<Currency> cur = Auctions.currency(l);
        if (e == null || cur.isEmpty()) return;
        boolean tips = Features.on(Features.UI_TOOLTIPS);
        boolean mine = l.seller.equals(viewer.getUUID());
        SimpleDateFormat df = new SimpleDateFormat("MM-dd HH:mm");

        // 1: seller
        ItemStack seller = Icons.named(head(l).getItem(), Component.literal(l.sellerName));
        seller.set(DataComponents.PROFILE, new ResolvableProfile(new GameProfile(l.seller, l.sellerName)));
        if (tips) {
            List<Component> t = new ArrayList<>();
            t.add(Msg.menu("ah_menu.seller_listings", "count", Auctions.activeCount(l.seller)));
            if (mine) t.add(Msg.menu("ah_menu.you"));
            Icons.addLore(seller, t);
        }
        set(row, 0, Button.display(seller));

        // 2: item
        ItemStack item = l.item.copyWithCount(Math.max(1, Math.min(l.left, l.item.getMaxStackSize())));
        List<Component> t2 = new ArrayList<>();
        t2.add(Msg.menu(l.auction ? "ah_menu.type_auction" : "ah_menu.type_buy_now"));
        t2.add(Msg.menu("ah_menu.listing", "id", l.id));
        boolean staff = Perm.has(viewer, Perm.AH_REMOVE);
        if (staff) t2.add(Msg.menu("ah_menu.staff_remove"));
        if (tips || staff) Icons.addLore(item, t2);
        set(row, 1, staff
                ? Button.of(item, (p, c) -> {
                    if (c.isShift() && c.isRight()) {
                        ConfirmMenu.ask(p, l.item.copyWithCount(1), List.of(Msg.menu("ah_menu.staff_remove")),
                                who -> {
                                    AhTrade.adminRemove(who, l);
                                    open(who);
                                }, this::open);
                    }
                })
                : Button.display(item));

        // 3: filler
        set(row, 2, Button.display(Icons.filler()));

        // 4-5: price (coin currencies use up to two coin types, largest first)
        Money shown = cur.get().of(l.auction ? (l.bids > 0 ? l.topBid : l.price) : l.price.multiply(java.math.BigInteger.valueOf(l.left)));
        List<ItemStack> price = PriceIcons.slots(shown, 2);
        List<Component> tp = new ArrayList<>();
        if (l.auction) {
            tp.add(Msg.menu(l.bids > 0 ? "ah_menu.current_bid" : "ah_menu.start_bid", "amount", e.format(shown)));
            tp.add(Msg.menu("ah_menu.next_bid", "amount", e.format(cur.get().of(Auctions.minBid(l)))));
            tp.add(Msg.menu("ah_menu.bids", "count", l.bids));
            if (viewer.getUUID().equals(l.topBidder)) tp.add(Msg.menu("ah_menu.highest"));
        } else {
            tp.add(Msg.menu("ah_menu.price_all", "amount", e.format(shown)));
        }
        for (int i = 0; i < 2; i++) {
            ItemStack p = i < price.size() ? price.get(i) : Icons.filler();
            if (tips && i < price.size()) Icons.addLore(p, tp);
            set(row, 3 + i, Button.display(p));
        }
        // 6: filler
        set(row, 5, Button.display(Icons.filler()));

        // 7: amount up for auction (static)
        Money balance = e.balance(viewer.getUUID(), cur.get());
        ItemStack amt = Icons.named(Items.CHEST, Msg.menu("ah_menu.lot", "count", l.left));
        amt.setCount(Math.max(1, Math.min(64, l.left)));
        set(row, 6, Button.display(amt));

        if (!l.auction) {
            // 8: Buy (the whole listing)
            Money total = shown;
            List<Component> t8 = new ArrayList<>();
            t8.add(Msg.menu("shop_menu.buy_line", "count", l.left, "item", l.item.getHoverName().getString()));
            t8.add(Msg.menu("shop_menu.total", "amount", e.format(total)));
            t8.add(Msg.menu("menu.balance_now", "balance", e.format(balance)));
            t8.add(balance.compareTo(total) >= 0 ? Msg.menu("menu.balance_after", "balance", e.format(balance.subtract(total))) : Msg.menu("menu.not_enough"));
            t8.add(Msg.menu("menu.click_confirm"));
            ItemStack buy = Icons.named(mine ? Items.GRAY_CONCRETE : Items.LIME_CONCRETE, Msg.menu("menu.buy"), tips ? t8 : List.of());
            set(row, 7, Button.of(buy, (p, c) -> {
                if (mine) {
                    Msg.send(p, "ah.own");
                    return;
                }
                ConfirmMenu.ask(p, l.item.copyWithCount(Math.max(1, Math.min(64, l.left))), t8.subList(0, t8.size() - 1), who -> {
                    AhTrade.buy(who, l);
                    open(who);
                }, this::open);
            }));
        } else {
            List<Component> t8 = new ArrayList<>();
            t8.add(Msg.menu("ah_menu.bid_line", "amount", e.format(cur.get().of(Auctions.minBid(l)))));
            t8.add(Msg.menu("menu.balance_now", "balance", e.format(balance)));
            t8.add(Msg.menu("menu.opens_bid"));
            ItemStack bid = Icons.named(mine ? Items.GRAY_CONCRETE : Items.LIGHT_BLUE_CONCRETE, Msg.menu("menu.bid"), tips ? t8 : List.of());
            set(row, 7, Button.of(bid, (p, c) -> {
                if (mine) {
                    Msg.send(p, "ah.own");
                    return;
                }
                new AhBidMenu(l, this).open(p);
            }));
        }

        // 9: time left
        ItemStack time = Icons.named(Items.CLOCK, Msg.menu("ah_menu.time_left", "time", timeLeft(l.ends)));
        if (tips) {
            Icons.addLore(time, List.of(Msg.menu("ah_menu.ends_at", "time", df.format(new Date(l.ends))),
                    Msg.menu("ah_menu.listed_at", "time", df.format(new Date(l.created)))));
        }
        set(row, 8, Button.display(time));
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(Icons.named(Items.BARRIER, Msg.menu("menu.close")), (p, c) -> close()));
        if (Features.on(Features.AH_CATEGORIES) && Features.on(Features.UI_CATEGORIES)) {
            navButton(1, Button.of(Icons.named(Items.BOOKSHELF, Msg.menu("ah_menu.category", "category", Msg.raw("ah_menu.cat_" + CATEGORIES[category]))),
                    (p, c) -> {
                        category = c.isRight() ? (category + CATEGORIES.length - 1) % CATEGORIES.length : (category + 1) % CATEGORIES.length;
                        page = 0;
                        refresh();
                    }));
        }
        if (Features.on(Features.UI_SORT)) {
            navButton(2, Button.of(Icons.named(Items.HOPPER, Msg.menu("menu.sort", "sort", Msg.raw("ah_menu.sort_" + SORTS[sort]))), (p, c) -> {
                sort = (sort + 1) % SORTS.length;
                page = 0;
                refresh();
            }));
        }
        if (Features.on(Features.AH_SEARCH) && Features.on(Features.UI_SEARCH)) {
            if (search.isEmpty()) {
                navButton(3, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("menu.search")), (p, c) ->
                        TextInput.ask(p, Msg.menu("menu.enter_search"), "", (who, text) -> {
                            search = text.toLowerCase(Locale.ROOT);
                            page = 0;
                            open(who);
                        }, this::open)));
            } else {
                navButton(3, Button.of(Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("shop_menu.clear_search", "text", search)), (p, c) -> {
                    search = "";
                    refresh();
                }));
            }
        }
        if (Features.on(Features.AH_MY_LISTINGS)) {
            navButton(4, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("ah_menu.my_listings")), (p, c) -> new AhMyListingsMenu(this).open(p)));
        }
        if (Features.on(Features.AH_COLLECT) && Perm.has(viewer, Perm.AH_COLLECT)) {
            boolean waiting = !CollectionBox.isEmpty(viewer.getUUID());
            navButton(5, Button.of(Icons.named(waiting ? Items.ENDER_CHEST : Items.CHEST, Msg.menu("ah_menu.collection"),
                    List.of(Msg.menu(waiting ? "ah_menu.collection_waiting" : "ah_menu.collection_empty"))),
                    (p, c) -> new AhCollectMenu(this).open(p)));
        }
    }
}
