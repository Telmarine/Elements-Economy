package com.tac5studios.elementseconomy.auction;

import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.shop.ShopStock;
import com.tac5studios.elementseconomy.shop.ShopTrade;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Collection box: money and items waiting. Click one to take it, or take everything. */
public final class AhCollectMenu extends PagedMenu<AhCollectMenu.Entry> {

    /** Either money in one currency, or one item stack at a box position. */
    public record Entry(String currency, BigInteger amount, ItemStack item, int index) {}

    private final AhBrowseMenu back;

    public AhCollectMenu(AhBrowseMenu back) {
        super(Msg.menu("ah_menu.collection"), Layout.SLOTS);
        this.back = back;
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("ah_menu.collection_empty");
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, BigInteger> m : CollectionBox.money(viewer.getUUID()).entrySet()) {
            out.add(new Entry(m.getKey(), m.getValue(), null, -1));
        }
        List<ItemStack> items = CollectionBox.items(viewer.getUUID(), Auctions.registries());
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) out.add(new Entry(null, null, items.get(i), i));
        }
        return out;
    }

    @Override
    protected void drawEntry(Entry en, int slot) {
        Economy e = Economy.get();
        if (en.item() == null) {
            ResourceLocation id = ResourceLocation.tryParse(en.currency());
            Optional<Currency> cur = e == null || id == null ? Optional.empty() : e.currency(id);
            if (cur.isEmpty()) return;
            set(slot, Button.of(Icons.named(cur.get().icon().getItem(), Component.literal(e.format(cur.get().of(en.amount()))),
                    List.of(Msg.menu("ah_menu.click_take"))), (p, c) -> {
                // Pay what is in the box now, not what it held when the menu was drawn.
                BigInteger now = CollectionBox.money(p.getUUID()).get(en.currency());
                BigInteger pay = now == null ? BigInteger.ZERO : Economy.payable(cur.get(), now);
                if (pay.signum() <= 0) {
                    refresh();
                    return;
                }
                Result r = e.deposit(p.getUUID(), cur.get().of(pay), Cause.command("/ah collect", p.getUUID()));
                if (r.success()) {
                    CollectionBox.takeMoney(p.getUUID(), en.currency(), pay);
                    Msg.send(p, "ah.collected", "count", 1);
                } else {
                    Msg.send(p, "general.currency_unavailable");
                }
                refresh();
            }));
            return;
        }
        ItemStack shown = Icons.addLore(en.item().copy(), List.of(Msg.menu("ah_menu.click_take")));
        set(slot, Button.of(shown, (p, c) -> {
            // Only hand it over if the box still holds this exact stack at this spot.
            List<ItemStack> now = CollectionBox.items(p.getUUID(), Auctions.registries());
            if (en.index() >= now.size() || !ItemStack.matches(now.get(en.index()), en.item())) {
                refresh();
                return;
            }
            ItemStack st = now.get(en.index());
            if (ShopStock.room(ShopTrade.inventory(p), st, st.getCount()) < st.getCount()) {
                Msg.send(p, "shop.no_room");
                return;
            }
            p.getInventory().add(st.copy());
            CollectionBox.removeItem(p.getUUID(), en.index());
            p.containerMenu.broadcastChanges();
            refresh();
        }));
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(Icons.named(Items.ARROW, Msg.menu("shop_menu.back")), (p, c) -> back.open(p)));
        navButton(5, Button.of(Icons.named(Items.HOPPER, Msg.menu("ah_menu.take_all")), (p, c) -> {
            int n = AhTrade.collectAll(p);
            Msg.send(p, n > 0 ? "ah.collected" : "ah.nothing", "count", n);
            refresh();
        }));
    }
}
