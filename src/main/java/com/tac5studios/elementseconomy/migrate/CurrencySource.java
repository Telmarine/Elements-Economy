package com.tac5studios.elementseconomy.migrate;

import com.tac5studios.elementseconomy.bridge.Bridges;
import com.tac5studios.elementseconomy.core.Accounts;
import com.tac5studios.elementseconomy.core.DigitalCurrency;
import com.tac5studios.elementseconomy.core.Economy;
import com.tac5studios.elementseconomy.core.ItemCurrency;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Holding;
import net.minecraft.server.MinecraftServer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A detected currency mod whose balances can be read while players are offline (EconomyCraft, Saro's Money,
 * Saro's Essentials, Numismatics, CobbleDollars, Never Enough Currency, BankSystem, OmniEconomy, Simple
 * Economy, SDM Economy 2.x). Reads every player Economy knows, plus the mod's own list when it has one.
 * Coins carried in inventories are not a balance and are not migrated.
 */
final class CurrencySource implements MigrationSource {

    private final Currency currency;

    CurrencySource(Currency currency) {
        this.currency = currency;
    }

    /** One source per readable currency. */
    static List<MigrationSource> all() {
        List<MigrationSource> out = new ArrayList<>();
        Economy e = Economy.get();
        if (e == null) return out;
        for (Currency c : e.currencies()) {
            if (c instanceof DigitalCurrency || c instanceof ItemCurrency) continue;
            if (!c.can(Capability.OFFLINE_READ)) continue;
            if (Bridges.providing(c.sourceMod())) continue; // our own money
            out.add(new CurrencySource(c));
        }
        return out;
    }

    @Override
    public String id() {
        return currency.sourceMod();
    }

    @Override
    public String name() {
        return currency.name().getString();
    }

    @Override
    public boolean present(MinecraftServer server) {
        return true;
    }

    @Override
    public Result read(MinecraftServer server) {
        Economy e = Economy.get();
        Map<String, Rate> rates = MigrationSource.rates();
        // Rates for currencies are per smallest unit; with no line, one whole unit = one digital coin.
        String key = currency.id().toString();
        Rate rate = Economy.rateFor(key).map(r -> new Rate(r, true))
                .orElse(new Rate(BigDecimal.ONE.movePointLeft(currency.decimals()), false));
        rates.put(key, rate);

        Set<UUID> players = new LinkedHashSet<>();
        List<String> keys = new ArrayList<>(Accounts.keys());
        keys.addAll(Storage.get().keys(Collections.BALANCES));
        for (String k : keys) {
            try {
                players.add(UUID.fromString(k));
            } catch (IllegalArgumentException ignored) {
                // not a player key
            }
        }
        if (currency.can(Capability.LIST_ALL)) {
            for (Holding h : e.top(currency, Integer.MAX_VALUE)) players.add(h.player());
        }

        List<Entry> out = new ArrayList<>();
        for (UUID id : players) {
            BigInteger units = e.balance(id, currency).amount();
            if (units.signum() > 0) out.add(new Entry(id, null, new BigDecimal(units).multiply(rate.value())));
        }
        return new Result(out, rates, 0);
    }
}
