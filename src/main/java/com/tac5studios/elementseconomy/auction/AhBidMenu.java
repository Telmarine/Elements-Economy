package com.tac5studios.elementseconomy.auction;

import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.ConfirmMenu;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.Menu;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Bid screen (3 rows, LOCKED): item · current bid · lowest next bid · your bid;
 * +1 step · +5 steps · type an amount · reset; cancel · confirm.
 * The bid is taken now and held until outbid (refunded to the collection box) or won.
 */
public final class AhBidMenu extends Menu {

    private final Listing listing;
    private final AhBrowseMenu back;
    private BigInteger bid;

    public AhBidMenu(Listing listing, AhBrowseMenu back) {
        super(3, Msg.menu("ah_menu.bid_title"));
        this.listing = listing;
        this.back = back;
        this.bid = Auctions.minBid(listing);
    }

    private BigInteger step() {
        BigInteger min = Auctions.minBid(listing);
        BigInteger base = listing.bids > 0 ? listing.topBid : listing.price;
        return min.subtract(base).max(BigInteger.ONE);
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        Economy e = Economy.get();
        Optional<Currency> cur = Auctions.currency(listing);
        if (e == null || cur.isEmpty() || !listing.active()) {
            set(13, Button.display(Icons.named(Items.BARRIER, Msg.menu("ah_menu.gone"))));
            set(18, Button.of(Icons.named(Items.ARROW, Msg.menu("shop_menu.back")), (p, c) -> back.open(p)));
            return;
        }
        Currency c0 = cur.get();
        BigInteger min = Auctions.minBid(listing);
        if (bid.compareTo(min) < 0) bid = min;

        set(4, Button.display(listing.item.copyWithCount(Math.max(1, Math.min(64, listing.left)))));
        set(10, Button.display(Icons.named(Items.GOLD_NUGGET, Msg.menu(listing.bids > 0 ? "ah_menu.current_bid" : "ah_menu.start_bid",
                "amount", e.format(c0.of(listing.bids > 0 ? listing.topBid : listing.price))))));
        set(11, Button.display(Icons.named(Items.GOLD_INGOT, Msg.menu("ah_menu.next_bid", "amount", e.format(c0.of(min))))));
        set(13, Button.display(Icons.named(Items.EMERALD, Msg.menu("ah_menu.your_bid", "amount", e.format(c0.of(bid))),
                List.of(Msg.menu("menu.balance_now", "balance", e.format(e.balance(viewer.getUUID(), c0)))))));

        set(14, Button.of(Icons.named(Items.LIME_DYE, Msg.menu("ah_menu.step_one", "amount", e.format(c0.of(step())))), (p, c) -> {
            bid = bid.add(step());
            refresh();
        }));
        set(15, Button.of(Icons.named(Items.GREEN_DYE, Msg.menu("ah_menu.step_five", "amount", e.format(c0.of(step().multiply(BigInteger.valueOf(5)))))), (p, c) -> {
            bid = bid.add(step().multiply(BigInteger.valueOf(5)));
            refresh();
        }));
        set(16, Button.of(Icons.named(Items.NAME_TAG, Msg.menu("ah_menu.type_amount")), (p, c) ->
                TextInput.ask(p, Msg.menu("menu.enter_price"), "", (who, text) -> {
                    c0.parse(text.trim()).ifPresentOrElse(v -> bid = v, () -> Msg.send(who, "general.bad_amount", "input", text));
                    open(who);
                }, this::open)));
        set(12, Button.of(Icons.named(Items.WATER_BUCKET, Msg.menu("ah_menu.reset")), (p, c) -> {
            bid = Auctions.minBid(listing);
            refresh();
        }));

        set(18, Button.of(Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("menu.no")), (p, c) -> back.open(p)));
        set(26, Button.of(Icons.named(Items.LIME_STAINED_GLASS_PANE, Msg.menu("ah_menu.confirm_bid"),
                List.of(Msg.menu("ah_menu.your_bid", "amount", e.format(c0.of(bid))), Msg.menu("ah_menu.bid_held"))), (p, c) -> {
            BigInteger amount = bid;
            ConfirmMenu.ask(p, listing.item.copyWithCount(1), List.of(Msg.menu("ah_menu.your_bid", "amount", e.format(c0.of(amount)))), who -> {
                AhTrade.bid(who, listing, amount);
                back.open(who);
            }, this::open);
        }));
    }
}
