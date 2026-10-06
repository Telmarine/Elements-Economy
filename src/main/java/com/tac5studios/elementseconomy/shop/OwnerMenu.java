package com.tac5studios.elementseconomy.shop;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.config.ShopConfig;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementseconomy.ui.PriceIcons;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The owner screen (auction-house template, LOCKED). Every item type in the shop (and its vault)
 * is a row; new items show "No price set" and stay hidden from customers until priced.
 * Sell shops list the items the owner wants to buy (add one from your hand).
 */
public final class OwnerMenu extends PagedMenu<ShopRow> {

    private final Shop shop;

    public OwnerMenu(Shop shop) {
        super(Component.literal(shop.name).append(" ✎"), Layout.ROWS);
        this.shop = shop;
    }

    @Override
    protected boolean stillValid(Player player) {
        return TradeMenu.near(player, shop);
    }

    @Override
    protected Component emptyText() {
        return Msg.menu(shop.type == ShopType.BUY ? "shop_menu.owner_empty_buy" : "shop_menu.owner_empty_sell");
    }

    /** BUY: pick up new item types from the container and vault as unpriced rows. */
    private void scan() {
        if (shop.type != ShopType.BUY) return;
        ServerLevel level = ShopTrade.level(viewer, shop);
        if (level == null) return;
        List<ItemStack> types = new ArrayList<>(ShopStock.types(ShopStock.shop(level, shop)));
        types.addAll(ShopStock.types(ShopStock.vault(level, shop)));
        boolean changed = false;
        for (ItemStack t : types) {
            String key = ShopStock.key(t);
            if (shop.rows.containsKey(key)) continue;
            ShopRow r = new ShopRow(key);
            r.item = t.copyWithCount(1);
            shop.rows.put(key, r);
            changed = true;
        }
        if (changed) Shops.save(shop);
    }

    @Override
    protected List<ShopRow> entries() {
        scan();
        List<ShopRow> out = new ArrayList<>();
        for (ShopRow r : shop.rows.values()) if (r.item != null) out.add(r);
        return out;
    }

    @Override
    protected void drawEntry(ShopRow r, int row) {
        Economy e = Economy.get();
        ServerLevel level = ShopTrade.level(viewer, shop);
        Optional<Currency> cur = ShopTrade.currency(r);

        // 1: stock (BUY) or wanted (SELL), with owner details
        ItemStack info;
        List<Component> tips = new ArrayList<>();
        if (shop.type == ShopType.BUY) {
            int inShop = level == null ? 0 : ShopStock.countShop(level, shop, r);
            int inVault = level == null ? 0 : ShopStock.countVault(level, shop, r);
            info = Icons.named(Items.PAPER, Msg.menu("shop_menu.in_shop", "shop", inShop, "vault", inVault));
            info.setCount(Math.max(1, Math.min(64, inShop)));
            if (shop.vault != null) tips.add(Msg.menu("shop_menu.restock_below", "count", ShopConfig.RESTOCK_BELOW.get()));
        } else {
            info = Icons.named(Items.HOPPER, Msg.menu("menu.wanted", "count", r.wanted));
            if (level != null) {
                tips.add(Msg.menu("shop_menu.in_shop", "shop", ShopStock.countShop(level, shop, r),
                        "vault", ShopStock.countVault(level, shop, r)));
            }
        }
        tips.add(Msg.menu("shop_menu.sold_today", "count", ShopDaily.soldToday(shop, r)));
        Icons.addLore(info, tips);
        set(row, 0, Button.display(info));

        set(row, 1, Button.display(r.item.copy()));
        set(row, 2, Button.display(Icons.filler()));

        // 4-5: price, or "No price set"
        if (r.priced() && cur.isPresent() && e != null) {
            List<ItemStack> price = PriceIcons.slots(cur.get().of(r.price), 2);
            for (int i = 0; i < 2; i++) set(row, 3 + i, Button.display(i < price.size() ? price.get(i) : Icons.filler()));
        } else {
            set(row, 3, Button.display(Icons.named(Items.BARRIER, Msg.menu("menu.no_price"))));
            set(row, 4, Button.display(Icons.filler()));
        }
        set(row, 5, Button.display(Icons.filler()));

        // 7: bundle size / daily limit
        List<Component> lim = new ArrayList<>();
        if (r.dailyLimit > 0) lim.add(Msg.menu("shop_menu.daily", "count", r.dailyLimit));
        set(row, 6, Button.display(Icons.named(Items.CHEST, Msg.menu("shop_menu.per", "count", r.per), lim)));

        // 8: edit -> pricing screen
        set(row, 7, Button.of(Icons.named(Items.WRITABLE_BOOK, Msg.menu("shop_menu.edit"), List.of(Msg.menu("menu.click_change"))),
                (p, c) -> new PricingMenu(shop, r, this).open(p)));

        // 9: for sale on/off
        boolean on = r.enabled;
        set(row, 8, Button.of(Icons.named(on ? Items.LIME_DYE : Items.GRAY_DYE,
                Msg.menu(on ? "shop_menu.on" : "shop_menu.off")), (p, c) -> {
            if (!r.priced()) {
                Msg.send(p, "shop.no_price", "item", r.item.getHoverName().getString(), "shop", shop.name);
                return;
            }
            r.enabled = !r.enabled;
            Shops.save(shop);
            refresh();
        }));
    }

    @Override
    protected void drawNav() {
        Economy e = Economy.get();
        // Open / closed (customers see it on the shop's sign). Esc closes this screen.
        boolean open = shop.open;
        navButton(0, Button.of(Icons.named(open ? Items.LIME_BANNER : Items.RED_BANNER,
                Msg.menu(open ? "shop_menu.shop_open" : "shop_menu.shop_closed"),
                List.of(Msg.menu("shop_menu.open_hint"), Msg.menu("menu.click_change"))), (p, c) -> {
            shop.open = !shop.open;
            Shops.save(shop);
            refresh();
        }));

        // Collect earnings / till
        List<Component> tillLines = new ArrayList<>();
        if (e != null) {
            for (Map.Entry<String, BigInteger> t : shop.till.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(t.getKey());
                Optional<Currency> c = id == null ? Optional.empty() : e.currency(id);
                c.ifPresent(cur -> tillLines.add(Component.literal(e.format(cur.of(t.getValue()))).withStyle(ChatFormatting.YELLOW)));
            }
        }
        if (tillLines.isEmpty()) tillLines.add(Msg.menu("shop_menu.till_empty"));
        if (shop.type == ShopType.BUY) {
            tillLines.add(Msg.menu("shop_menu.click_collect"));
            navButton(1, Button.of(Icons.named(Items.GOLD_INGOT, Msg.menu("shop_menu.earnings"), tillLines), (p, c) -> {
                collect(p);
                refresh();
            }));
        } else {
            tillLines.add(Msg.menu("shop_menu.click_funds"));
            navButton(1, Button.of(Icons.named(Items.GOLD_INGOT, Msg.menu("shop_menu.funds"), tillLines), (p, c) -> {
                if (c.isRight()) {
                    collect(p);
                    refresh();
                } else {
                    addFunds(p);
                }
            }));
            navButton(2, Button.of(Icons.named(Items.HOPPER, Msg.menu("shop_menu.add_item"),
                            List.of(Msg.menu("servershop.menu_add_shift"), Msg.menu("shop_menu.add_item_hint"))),
                    (p, c) -> addFromHand(p)));
        }

        if (Features.on(Features.PS_RENAME) && Perm.has(viewer, Perm.SHOP_RENAME)) {
            navButton(3, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("shop_menu.rename")), (p, c) ->
                    TextInput.ask(p, Msg.menu("menu.enter_name"), shop.name, (who, text) -> {
                        ShopCommands.rename(who, shop, text);
                        open(who);
                    }, this::open)));
        }
        List<Component> storageLore = new ArrayList<>();
        if (shop.vault != null) storageLore.add(Msg.menu("shop_menu.vault_linked", "pos", shop.vault.toShortString()));
        navButton(4, Button.of(Icons.named(Items.CHEST, Msg.menu("shop_menu.open_storage"), storageLore), (p, c) -> openStorage(p)));

        // Claim access: customers reach the shop through the owner's claim without a claim warning.
        if (Features.on(Features.PS_CLAIM_ACCESS)) {
            boolean on = shop.claimAccess;
            navButton(5, Button.of(Icons.named(on ? Items.OAK_DOOR : Items.IRON_DOOR,
                    Msg.menu(on ? "shop_menu.claim_access_on" : "shop_menu.claim_access_off"),
                    List.of(Msg.menu("shop_menu.claim_access_hint"), Msg.menu("menu.click_change"))), (p, c) -> {
                shop.claimAccess = !shop.claimAccess;
                Shops.save(shop);
                refresh();
            }));
        }
    }

    /** Pay the till into the owner's balance (each currency). */
    private void collect(ServerPlayer p) {
        Economy e = Economy.get();
        if (e == null) return;
        boolean any = false;
        for (Map.Entry<String, BigInteger> t : new ArrayList<>(shop.till.entrySet())) {
            ResourceLocation id = ResourceLocation.tryParse(t.getKey());
            Optional<Currency> c = id == null ? Optional.empty() : e.currency(id);
            if (c.isEmpty()) continue;
            Money m = c.get().of(t.getValue());
            Result r = e.deposit(p.getUUID(), m, Cause.shop(shop.id, p.getUUID()).withReason("collect"));
            if (r.success()) {
                shop.addTill(t.getKey(), t.getValue().negate());
                Msg.send(p, "shop.collected", "amount", e.format(m), "shop", shop.name);
                any = true;
            }
        }
        if (any) Shops.save(shop);
        else Msg.send(p, "shop.nothing_to_collect");
    }

    /** Sell shops: move money from the owner into the till. */
    private void addFunds(ServerPlayer p) {
        TextInput.ask(p, Msg.menu("shop_menu.enter_funds"), "", (who, text) -> {
            Economy e = Economy.get();
            if (e != null && e.primaryCurrency() != null) {
                Currency cur = e.primaryCurrency();
                Optional<Money> m = e.parse(cur, text);
                if (m.isEmpty()) {
                    Msg.send(who, "general.bad_amount", "input", text);
                } else {
                    Result r = e.withdraw(who.getUUID(), m.get(), Cause.shop(shop.id, who.getUUID()).withReason("funds"));
                    if (r.success()) {
                        shop.addTill(cur.id().toString(), m.get().amount());
                        Shops.save(shop);
                        Msg.send(who, "shop.funds_added", "amount", e.format(m.get()), "shop", shop.name);
                    } else {
                        Msg.send(who, "pay.not_enough", "balance", e.format(e.balance(who.getUUID(), cur)));
                    }
                }
            }
            open(who);
        }, this::open);
    }

    /** Sell shops: the item in the owner's hand becomes a new row. */
    /** Sell shops: shift-click an item in your inventory to add it to what the shop buys. */
    @Override
    protected boolean onInventoryShiftClick(ServerPlayer p, ItemStack stack) {
        if (shop.type != ShopType.SELL) return false;
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

    private void add(ItemStack hand) {
        String key = ShopStock.key(hand);
        if (!shop.rows.containsKey(key)) {
            ShopRow r = new ShopRow(key);
            r.item = hand.copyWithCount(1);
            shop.rows.put(key, r);
            Shops.save(shop);
        }
        refresh();
    }

    /** Opens the real container for the owner. */
    private void openStorage(ServerPlayer p) {
        ServerLevel level = ShopTrade.level(p, shop);
        if (level == null) return;
        MenuProvider provider = level.getBlockState(shop.pos()).getMenuProvider(level, shop.pos());
        if (provider == null && level.getBlockEntity(shop.pos()) instanceof MenuProvider mp) provider = mp;
        if (provider == null) return;
        MenuProvider finalProvider = provider;
        p.server.tell(new net.minecraft.server.TickTask(p.server.getTickCount(), () -> p.openMenu(finalProvider)));
    }
}
