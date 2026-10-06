package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/** MiguelEconomy: MoneyUtils.getBalance / setMoney on the player. Online players only. */
final class MiguelEconomyAdapter extends ReflectBackend {

    private static final String UTILS = "com.miguel.economy.utils.MoneyUtils";

    MiguelEconomyAdapter() {
        super(new ExternalCurrency("migueleconomy", "money", "Coins", 0, EnumSet.noneOf(Capability.class), null, null));
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(UTILS, "getBalance", 1) && Reflect.hasMethod(UTILS, "setMoney", 2);
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        return p == null ? null : fromLong(Reflect.callStatic(UTILS, "getBalance", p));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        Reflect.callStatic(UTILS, "setMoney", p, toLong(amount));
        return true;
    }
}
