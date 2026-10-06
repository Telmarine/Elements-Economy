package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Simple Economy (sennecools): static EconomyManager by server + UUID, doubles. Works offline. */
final class SimpleEconomyAdapter extends ReflectBackend {

    private static final String MANAGER = "com.simpleeconomy.economy.EconomyManager";

    SimpleEconomyAdapter() {
        super(new ExternalCurrency("simpleeconomy", "money", "Coins", 2,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), null, null));
    }

    @Override
    protected boolean ready() {
        return server() != null && Reflect.hasMethod(MANAGER, "getBalance", 2) && Reflect.hasMethod(MANAGER, "setBalance", 3);
    }

    @Override
    protected BigInteger read(UUID id) {
        return fromDouble(Reflect.callStatic(MANAGER, "getBalance", server(), id));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Reflect.callStatic(MANAGER, "setBalance", server(), id, toDouble(amount));
        return true;
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        Object top = Reflect.callStatic(MANAGER, "getTopBalances", server());
        if (top instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map.Entry<?, ?> e && e.getKey() instanceof UUID id) out.add(holding(id, fromDouble(e.getValue())));
            }
        }
        return out;
    }
}
