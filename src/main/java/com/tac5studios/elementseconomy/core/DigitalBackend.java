package com.tac5studios.elementseconomy.core;

import com.google.gson.JsonObject;
import com.tac5studios.elementseconomy.storage.Collections;
import com.tac5studios.elementseconomy.storage.Storage;
import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.Holding;
import com.tac5studios.elementsvault.Result;
import com.tac5studios.elementsvault.backend.CurrencyBackend;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Digital balances in the data store: collection "balances", key = player UUID,
 * value = { "elements_economy:digital": "125000" }. Works for offline players.
 */
public final class DigitalBackend implements CurrencyBackend {

    private final DigitalCurrency currency;

    public DigitalBackend(DigitalCurrency currency) {
        this.currency = currency;
    }

    @Override
    public ResourceLocation id() {
        return DigitalCurrency.ID;
    }

    @Override
    public Currency currency() {
        return currency;
    }

    @Override
    public boolean isAvailable() {
        return Storage.running();
    }

    /** True when the player has a saved balance (used for the starting balance). */
    public boolean hasAccount(UUID player) {
        JsonObject o = Storage.get().get(Collections.BALANCES, player.toString(), JsonObject.class);
        return o != null && o.has(key());
    }

    @Override
    public BigInteger balance(UUID player) {
        JsonObject o = Storage.get().get(Collections.BALANCES, player.toString(), JsonObject.class);
        if (o == null || !o.has(key())) return BigInteger.ZERO;
        try {
            return new BigInteger(o.get(key()).getAsString());
        } catch (RuntimeException e) {
            return BigInteger.ZERO;
        }
    }

    private void write(UUID player, BigInteger amount) {
        JsonObject o = Storage.get().get(Collections.BALANCES, player.toString(), JsonObject.class);
        if (o == null) o = new JsonObject();
        o.addProperty(key(), amount.toString());
        Storage.get().put(Collections.BALANCES, player.toString(), o);
        Storage.moneyChanged();
    }

    private String key() {
        return DigitalCurrency.ID.toString();
    }

    @Override
    public Result withdraw(UUID player, BigInteger amount, Cause cause) {
        BigInteger before = balance(player);
        if (before.compareTo(amount) < 0) {
            return Result.fail(Result.Reason.INSUFFICIENT_FUNDS, currency.of(amount), Component.literal("Not enough money"));
        }
        BigInteger after = before.subtract(amount);
        write(player, after);
        return Result.ok(currency.of(amount), currency.of(before), currency.of(after));
    }

    @Override
    public Result deposit(UUID player, BigInteger amount, Cause cause) {
        BigInteger before = balance(player);
        BigInteger after = before.add(amount);
        write(player, after);
        return Result.ok(currency.of(amount), currency.of(before), currency.of(after));
    }

    @Override
    public Result set(UUID player, BigInteger amount, Cause cause) {
        BigInteger before = balance(player);
        write(player, amount);
        return Result.ok(currency.of(amount.subtract(before).abs()), currency.of(before), currency.of(amount));
    }

    @Override
    public List<Holding> top(int limit) {
        List<Holding> all = new ArrayList<>();
        for (String k : Storage.get().keys(Collections.BALANCES)) {
            try {
                UUID id = UUID.fromString(k);
                BigInteger b = balance(id);
                if (b.signum() > 0) all.add(new Holding(id, currency.of(b)));
            } catch (IllegalArgumentException ignored) {
                // not a player key
            }
        }
        all.sort(Comparator.comparing((Holding h) -> h.money().amount()).reversed());
        return all.size() > limit ? all.subList(0, limit) : all;
    }
}
