package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Lightman's Currency 2.x, main coin chain, wallet + inventory (MoneyAPI). Online players only.
 * Amounts are the chain's core value (the smallest coin = 1).
 */
final class LightmansAdapter extends ReflectBackend {

    private static final String MONEY_API = "io.github.lightman314.lightmanscurrency.api.money.MoneyAPI";
    private static final String COIN_VALUE = "io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue";

    private String key;

    LightmansAdapter() {
        super(new ExternalCurrency("lightmanscurrency", "main", "Lightman's Coins", 0, EnumSet.noneOf(Capability.class),
                "lightmanscurrency", "lightmanscurrency:coin_gold"));
    }

    @Override
    protected boolean ready() {
        if (!Reflect.has(MONEY_API) || !Reflect.has(COIN_VALUE)) return false;
        Object one = Reflect.callStatic(COIN_VALUE, "fromNumber", "main", 1L);
        key = (String) Reflect.call(one, "getUniqueName");
        return key != null;
    }

    private Object handler(ServerPlayer p) {
        Object api = Reflect.callStatic(MONEY_API, "getApi");
        return Reflect.call(api, "GetPlayersMoneyHandler", p);
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        if (p == null) return null;
        Object view = Reflect.call(handler(p), "getStoredMoney");
        Object value = Reflect.call(view, "valueOf", key);
        return fromLong(Reflect.call(value, "getCoreValue"));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        BigInteger now = read(id);
        Object h = handler(p);
        int cmp = amount.compareTo(now);
        if (cmp == 0) return true;
        Object diff = Reflect.callStatic(COIN_VALUE, "fromNumber", "main", toLong(amount.subtract(now).abs()));
        String op = cmp > 0 ? "insertMoney" : "extractMoney";
        // Simulate first: the leftover must be empty, or nothing is changed.
        Object left = Reflect.call(h, op, diff, true);
        if (!Boolean.TRUE.equals(Reflect.call(left, "isEmpty"))) return false;
        left = Reflect.call(h, op, diff, false);
        return Boolean.TRUE.equals(Reflect.call(left, "isEmpty"));
    }
}
