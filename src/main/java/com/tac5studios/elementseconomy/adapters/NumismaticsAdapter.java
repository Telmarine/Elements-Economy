package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import com.tac5studios.elementsvault.Holding;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create: Numismatics bank accounts (spurs). Works offline. Shown as Numismatics coins.
 * Accounts are only created when money is added, so reading never grants starter coins.
 */
final class NumismaticsAdapter extends ReflectBackend {

    private static final String MOD = "dev.ithundxr.createnumismatics.Numismatics";
    private static final String TYPE = "dev.ithundxr.createnumismatics.content.backend.BankAccount$Type";

    NumismaticsAdapter() {
        super(new ExternalCurrency("numismatics", "bank", "Spurs", 0,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE, Capability.LIST_ALL), "numismatics", null));
    }

    private static Object bank() {
        return Reflect.staticField(MOD, "BANK");
    }

    @Override
    protected boolean ready() {
        return Reflect.has(MOD) && bank() != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        Object acc = Reflect.call(bank(), "getAccount", id);
        return acc == null ? BigInteger.ZERO : fromLong(Reflect.call(acc, "getBalance"));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Object acc = Reflect.call(bank(), "getOrCreateAccount", id, Reflect.enumConstant(TYPE, "PLAYER"));
        Reflect.call(acc, "setBalance", toInt(amount));
        return true;
    }

    @Override
    protected List<Holding> list() {
        List<Holding> out = new ArrayList<>();
        Object accounts = Reflect.field(bank(), "accounts");
        if (accounts instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object acc = e.getValue();
                if (!(e.getKey() instanceof UUID id)) continue;
                Object type = Reflect.field(acc, "type");
                if (type == null || !"PLAYER".equals(type.toString())) continue;
                out.add(holding(id, fromLong(Reflect.call(acc, "getBalance"))));
            }
        }
        return out;
    }
}
