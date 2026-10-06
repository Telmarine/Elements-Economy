package com.tac5studios.elementseconomy.auction;

import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.perms.Perm;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.ConfirmMenu;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.PagedMenu;
import com.tac5studios.elementseconomy.ui.PriceIcons;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** My listings: active, sold and expired rows; active ones can be cancelled. */
public final class AhMyListingsMenu extends PagedMenu<Listing> {

    private final AhBrowseMenu back;

    public AhMyListingsMenu(AhBrowseMenu back) {
        super(Msg.menu("ah_menu.my_listings"), Layout.ROWS);
        this.back = back;
    }

    @Override
    protected Component emptyText() {
        return Msg.menu("ah_menu.no_listings");
    }

    @Override
    protected List<Listing> entries() {
        List<Listing> out = new ArrayList<>();
        for (Listing l : Auctions.bySeller(viewer.getUUID())) if (l.item != null) out.add(l);
        out.sort(Comparator.comparing((Listing l) -> !l.active()).thenComparing(Comparator.comparingLong((Listing l) -> l.created).reversed()));
        return out;
    }

    @Override
    protected void drawEntry(Listing l, int row) {
        Economy e = Economy.get();
        Optional<Currency> cur = Auctions.currency(l);
        String status = switch (l.status) {
            case ACTIVE -> "ah_menu.status_active";
            case SOLD -> "ah_menu.status_sold";
            case EXPIRED -> "ah_menu.status_expired";
            case CANCELLED -> "ah_menu.status_cancelled";
        };
        set(row, 0, Button.display(Icons.named(l.active() ? Items.LIME_DYE : Items.GRAY_DYE, Msg.menu(status))));
        set(row, 1, Button.display(l.item.copyWithCount(Math.max(1, Math.min(64, l.quantity)))));
        set(row, 2, Button.display(Icons.named(Items.CHEST, Msg.menu("ah_menu.lot", "count", l.quantity))));
        if (e != null && cur.isPresent()) {
            java.math.BigInteger amount = l.auction ? (l.bids > 0 ? l.topBid : l.price) : l.price.multiply(java.math.BigInteger.valueOf(l.quantity));
            List<ItemStack> price = PriceIcons.slots(cur.get().of(amount), 2);
            for (int i = 0; i < 2; i++) set(row, 3 + i, Button.display(i < price.size() ? price.get(i) : Icons.filler()));
        } else {
            for (int i = 0; i < 2; i++) set(row, 3 + i, Button.display(Icons.filler()));
        }
        set(row, 5, Button.display(Icons.filler()));
        set(row, 6, Button.display(Icons.filler()));
        if (l.active() && Features.on(Features.AH_CANCEL) && Perm.has(viewer, Perm.AH_CANCEL)) {
            set(row, 7, Button.of(Icons.named(Items.LAVA_BUCKET, Msg.menu("ah_menu.cancel"),
                    List.of(Msg.menu(l.auction && l.bids > 0 ? "ah_menu.cancel_has_bids" : "ah_menu.cancel_hint"))), (p, c) ->
                    ConfirmMenu.ask(p, l.item.copyWithCount(1), List.of(Msg.menu("ah_menu.cancel_hint")), who -> {
                        AhTrade.cancel(who, l);
                        open(who);
                    }, this::open)));
        } else {
            set(row, 7, Button.display(Icons.filler()));
        }
        set(row, 8, Button.display(Icons.named(Items.CLOCK, Msg.menu(l.active() ? "ah_menu.time_left" : "ah_menu.ended",
                "time", AhBrowseMenu.timeLeft(l.ends)))));
    }

    @Override
    protected void drawNav() {
        navButton(0, Button.of(Icons.named(Items.ARROW, Msg.menu("shop_menu.back")), (p, c) -> back.open(p)));
    }
}
