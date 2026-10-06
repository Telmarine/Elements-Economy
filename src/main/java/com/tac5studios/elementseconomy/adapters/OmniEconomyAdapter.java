package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** OmniEconomy: static Economy by server + UUID, whole numbers (int). Works offline. */
final class OmniEconomyAdapter extends ReflectBackend {

    private static final String ECO = "com.saunhardy.omnieconomy.core.Economy";

    OmniEconomyAdapter() {
        super(new ExternalCurrency("omnieconomy", "money", "Coins", 0,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), null, null));
    }

    @Override
    protected boolean ready() {
        return server() != null && Reflect.hasMethod(ECO, "getBalance", 2) && Reflect.hasMethod(ECO, "setBalance", 3);
    }

    @Override
    protected BigInteger read(UUID id) {
        return fromLong(Reflect.callStatic(ECO, "getBalance", server(), id));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Object result = Reflect.callStatic(ECO, "setBalance", server(), id, toInt(amount));
        return result instanceof Number n && n.longValue() == toInt(amount);
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        Object top = Reflect.callStatic(ECO, "getBaltop", server(), Integer.MAX_VALUE);
        if (top instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map.Entry<?, ?> e && e.getKey() instanceof UUID id) out.add(holding(id, fromLong(e.getValue())));
            }
        }
        return out;
    }
}
