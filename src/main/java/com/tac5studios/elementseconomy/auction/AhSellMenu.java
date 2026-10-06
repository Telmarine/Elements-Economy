package com.tac5studios.elementseconomy.auction;

import com.tac5studios.elementseconomy.config.AuctionConfig;
import com.tac5studios.elementseconomy.config.Features;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.messages.Msg;
import com.tac5studios.elementseconomy.ui.Button;
import com.tac5studios.elementseconomy.ui.Icons;
import com.tac5studios.elementseconomy.ui.Menu;
import com.tac5studios.elementseconomy.ui.TextInput;
import com.tac5studios.elementsvault.Currency;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Sell screen (/ah sell, 3 rows, LOCKED): the item in hand · price · buy now / auction ·
 * duration · fee · cancel · confirm. The item stays in the player's hand until confirm.
 */
public final class AhSellMenu extends Menu {

    private final ItemStack item;
    private BigInteger price = BigInteger.ZERO;
    private boolean auction;
    private int duration;

    public AhSellMenu(ItemStack item) {
        super(3, Msg.menu("ah_menu.sell_title"));
        this.item = item.copy();
        this.auction = !Features.on(Features.AH_BUY_NOW);
    }

    private List<Integer> durations() {
        List<Integer> d = new ArrayList<>(AuctionConfig.DURATIONS.get());
        if (d.isEmpty()) d.add(24);
        return d;
    }

    @Override
    protected void draw() {
        for (int i = 0; i < size(); i++) set(i, Button.display(Icons.filler()));
        Economy e = Economy.get();
        if (e == null || e.primaryCurrency() == null) return;
        Currency cur = e.primaryCurrency();
        List<Integer> durs = durations();
        int hours = durs.get(Math.min(duration, durs.size() - 1));

        set(4, Button.display(item.copy()));

        String priceText = price.signum() > 0 ? e.format(cur.of(price)) : Msg.raw("menu.no_price");
        set(10, Button.of(Icons.named(Items.GOLD_INGOT, Msg.menu(auction ? "ah_menu.start_bid_label" : "ah_menu.price_each_label"),
                List.of(com.tac5studios.elementseconomy.util.Text.color(priceText).withStyle(s -> s.withItalic(false)), Msg.menu("menu.click_change"))), (p, c) ->
                TextInput.ask(p, Msg.menu("menu.enter_price"), "", (who, text) -> {
                    cur.parse(text.trim()).ifPresentOrElse(v -> price = v, () -> Msg.send(who, "general.bad_amount", "input", text));
                    open(who);
                }, this::open)));

        boolean canSwitch = Features.on(Features.AH_BUY_NOW) && Features.on(Features.AH_BIDS);
        set(12, Button.of(Icons.named(auction ? Items.LIGHT_BLUE_CONCRETE : Items.LIME_CONCRETE,
                Msg.menu(auction ? "ah_menu.type_auction" : "ah_menu.type_buy_now"),
                canSwitch ? List.of(Msg.menu("menu.click_change")) : List.of()), (p, c) -> {
            if (!canSwitch) return;
            auction = !auction;
            refresh();
        }));

        set(14, Button.of(Icons.named(Items.CLOCK, Msg.menu("ah_menu.duration", "hours", hours), List.of(Msg.menu("menu.click_change"))), (p, c) -> {
            duration = c.isRight() ? (duration + durs.size() - 1) % durs.size() : (duration + 1) % durs.size();
            refresh();
        }));

        BigInteger value = auction ? price : price.multiply(BigInteger.valueOf(item.getCount()));
        BigInteger fee = AhTrade.fee(viewer, cur, value);
        if (fee.signum() > 0) {
            set(16, Button.display(Icons.named(Items.PAPER, Msg.menu("ah_menu.fee", "amount", e.format(cur.of(fee))))));
        }

        set(18, Button.of(Icons.named(Items.RED_STAINED_GLASS_PANE, Msg.menu("menu.no")), (p, c) -> close()));
        List<Component> summary = new ArrayList<>();
        summary.add(Msg.menu("shop_menu.sell_line", "count", item.getCount(), "item", item.getHoverName().getString()));
        if (price.signum() > 0) summary.add(com.tac5studios.elementseconomy.util.Text.color(priceText).withStyle(s -> s.withItalic(false)));
        if (fee.signum() > 0) summary.add(Msg.menu("ah_menu.fee", "amount", e.format(cur.of(fee))));
        set(26, Button.of(Icons.named(Items.LIME_STAINED_GLASS_PANE, Msg.menu("ah_menu.confirm_list"), summary), (p, c) -> {
            if (price.signum() <= 0) {
                Msg.send(p, "ah.price_needed");
                return;
            }
            close();
            AhTrade.list(p, item, auction, price, hours);
        }));
    }
}
