package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Arcadia Lib's economy (behind Arcadia AH): static EconomyService on the server player. Online only.
 * Its backend may itself be Numismatics, an emerald wallet or one configured item.
 */
final class ArcadiaAdapter extends ReflectBackend {

    private static final String SERVICE = "com.arcadia.lib.economy.EconomyService";

    ArcadiaAdapter() {
        super(new ExternalCurrency("arcadia_lib", "money", "Arcadia Money", 0, EnumSet.noneOf(Capability.class), null, null));
    }

    @Override
    protected boolean ready() {
        return Reflect.has(SERVICE) && Boolean.TRUE.equals(Reflect.callStatic(SERVICE, "isAvailable"));
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        return p == null ? null : fromLong(Reflect.callStatic(SERVICE, "getBalance", p));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        BigInteger now = read(id);
        int cmp = amount.compareTo(now);
        if (cmp == 0) return true;
        long diff = toLong(amount.subtract(now).abs());
        return Boolean.TRUE.equals(Reflect.callStatic(SERVICE, cmp > 0 ? "add" : "deduct", p, diff));
    }
}
