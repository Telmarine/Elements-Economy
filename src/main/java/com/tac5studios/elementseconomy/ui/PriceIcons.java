package com.tac5studios.elementseconomy.ui;

import com.tac5studios.elementseconomy.core.Amounts;
import com.tac5studios.elementseconomy.core.ItemCurrency;
import com.tac5studios.elementseconomy.currency.CurrencyDisplay;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Items for the price columns. Coin currencies show the real coins (largest first, up to {@code slots}
 * coin types); other currencies show their icon named with the amount.
 */
public final class PriceIcons {

    private PriceIcons() {}

    public static List<ItemStack> slots(Money money, int slots) {
        Currency c = money.currency();
        if (c instanceof ItemCurrency ic) {
            List<ItemStack> coins = CurrencyDisplay.priceSlots(ic.namespace(), Amounts.toLong(money.amount()), slots);
            if (!coins.isEmpty()) return coins;
        }
        List<ItemStack> out = new ArrayList<>();
        out.add(Icons.named(c.icon().getItem(), c.format(money.amount()).copy().withStyle(ChatFormatting.YELLOW)));
        return out;
    }
}
