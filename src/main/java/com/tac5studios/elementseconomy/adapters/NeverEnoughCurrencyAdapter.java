package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/** Never Enough Currency 2: CurrencyData (overworld saved data), cents per UUID. Works offline. */
final class NeverEnoughCurrencyAdapter extends ReflectBackend {

    private static final String DATA = "com.zundrel.currency.CurrencyData";

    NeverEnoughCurrencyAdapter() {
        super(new ExternalCurrency("currency", "account", "Dollars", 2,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE), null, null));
    }

    private static Object data() {
        return Reflect.callStatic(DATA, "get", server().overworld());
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(DATA, "account", 1) && server() != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        return fromLong(Reflect.call(data(), "account", id));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Reflect.call(data(), "setAccount", id, toLong(amount)); // marks itself dirty
        return true;
    }
}
