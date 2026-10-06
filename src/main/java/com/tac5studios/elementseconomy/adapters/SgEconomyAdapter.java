package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/** SG-Economy API: static methods on the player entity. Online players only. Decimal mode = 2 decimals. */
final class SgEconomyAdapter extends ReflectBackend {

    private static final String API = "net.sirgrantd.sg_economy.api.SGEconomyApi";

    private SgEconomyAdapter(int decimals) {
        super(new ExternalCurrency("sg_economy", "money", "Coins", decimals, EnumSet.noneOf(Capability.class), null, null));
    }

    static SgEconomyAdapter create() {
        int d = 0;
        try {
            d = Boolean.TRUE.equals(Reflect.callStatic(API, "isDecimalSystem")) ? 2 : 0;
        } catch (RuntimeException ignored) {
            // older version: whole numbers
        }
        return new SgEconomyAdapter(d);
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(API, "getBalance", 1) && Reflect.hasMethod(API, "setBalance", 2);
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        return p == null ? null : fromDouble(Reflect.callStatic(API, "getBalance", p));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        return p != null && Boolean.TRUE.equals(Reflect.callStatic(API, "setBalance", p, toDouble(amount)));
    }
}
