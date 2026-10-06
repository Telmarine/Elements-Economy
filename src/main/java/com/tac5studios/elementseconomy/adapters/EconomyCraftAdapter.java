package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** EconomyCraft 1.8+ API (EconomyCraftApi.get(server).balances()). By UUID, works offline. */
final class EconomyCraftAdapter extends ReflectBackend {

    private static final String API = "com.reazip.economycraft.api.v1.EconomyCraftApi";

    EconomyCraftAdapter() {
        super(new ExternalCurrency("economycraft", "money", "Coins", 0,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), null, null));
    }

    private static Object api() {
        return Reflect.callStatic(API, "get", server());
    }

    @Override
    protected boolean ready() {
        return Reflect.has(API) && server() != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        return fromLong(Reflect.call(Reflect.call(api(), "balances"), "getBalance", id));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Object result = Reflect.call(Reflect.call(api(), "balances"), "setMoney", id, toLong(amount));
        return Boolean.TRUE.equals(Reflect.call(result, "successful"));
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        Object entries = Reflect.call(Reflect.call(api(), "leaderboard"), "getLeaderboardEntries", Integer.MAX_VALUE);
        if (entries instanceof List<?> l) {
            for (Object e : l) {
                Object id = Reflect.call(e, "playerId");
                if (id instanceof UUID u) out.add(holding(u, fromLong(Reflect.call(e, "balance"))));
            }
        }
        return out;
    }
}
