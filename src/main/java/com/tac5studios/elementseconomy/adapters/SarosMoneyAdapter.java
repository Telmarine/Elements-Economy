package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saro's Money: BalanceManager.getBalance / setBalance by UUID text, euros as doubles. Works offline. */
final class SarosMoneyAdapter extends ReflectBackend {

    private static final String MANAGER = "de.sarocesch.sarosmoneymod.data.BalanceManager";

    SarosMoneyAdapter() {
        super(new ExternalCurrency("saros__money_mod", "bank", "Euro", 2,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), null, null));
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(MANAGER, "getBalance", 1) && Reflect.hasMethod(MANAGER, "setBalance", 2);
    }

    @Override
    protected BigInteger read(UUID id) {
        return fromDouble(Reflect.callStatic(MANAGER, "getBalance", id.toString()));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Reflect.callStatic(MANAGER, "setBalance", id.toString(), toDouble(amount));
        return true;
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        Object all = Reflect.callStatic(MANAGER, "loadBalances", server());
        if (all instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                try {
                    out.add(holding(UUID.fromString(String.valueOf(e.getKey())), fromDouble(e.getValue())));
                } catch (IllegalArgumentException ignored) {
                    // not a player id
                }
            }
        }
        return out;
    }
}
