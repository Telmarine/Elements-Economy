package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/**
 * KROIA BankSystem 2.x: the player's personal money bank (banksystem:money), by UUID. Works offline.
 * Raw amounts are hundredths, so 2 decimals. Version 1.x has a different API and is not covered.
 */
final class BankSystemAdapter extends ReflectBackend {

    private static final String MOD = "net.kroia.banksystem.BankSystemMod";
    private static final String MONEY_ITEM = "net.kroia.banksystem.minecraft.item.custom.money.MoneyItem";

    BankSystemAdapter() {
        super(new ExternalCurrency("banksystem", "money", "Money", 2,
                EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE), "banksystem", "banksystem:money"));
    }

    private static Object manager() {
        Object api = Reflect.callStatic(MOD, "getAPI");
        Object mgr = Reflect.call(api, "getServerBankManager");
        return Reflect.call(mgr, "getSync"); // null on a slave server
    }

    private static Object moneyId() {
        return Reflect.callStatic(MONEY_ITEM, "getItemID");
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(MOD, "getAPI", 0) && Reflect.hasMethod(MONEY_ITEM, "getItemID", 0) && manager() != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        Object mgr = manager();
        if (mgr == null) return null;
        Object bank = Reflect.call(mgr, "getPersonalBank", id, moneyId());
        return bank == null ? BigInteger.ZERO : fromLong(Reflect.call(bank, "getBalance"));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        Object mgr = manager();
        if (mgr == null) return false;
        Object bank = Reflect.call(mgr, "getOrCreatePersonalBank", id, moneyId());
        return bank != null && Boolean.TRUE.equals(Reflect.call(bank, "setBalance", toLong(amount)));
    }
}
