package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;

/** Better Economy (Enderman Bank): betterecon:balance attachment, whole dollars (int). Online players only. */
final class BetterEconomyAdapter extends ReflectBackend {

    private static final String TYPES = "net.ultimporks.betterecon.init.ModAttachmentTypes";

    BetterEconomyAdapter() {
        super(new ExternalCurrency("betterecon", "bank", "Dollars", 0, EnumSet.noneOf(Capability.class), "betterecon", null));
    }

    private static Object balance(ServerPlayer p) {
        Object holder = Reflect.staticField(TYPES, "BALANCE");
        Object type = holder instanceof Supplier<?> s ? s.get() : Reflect.call(holder, "get");
        return p.getData((AttachmentType<?>) type);
    }

    @Override
    protected boolean ready() {
        return Reflect.has(TYPES) && Reflect.staticField(TYPES, "BALANCE") != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        return p == null ? null : fromLong(Reflect.call(balance(p), "getBalance"));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        Reflect.call(balance(p), "setBalance", toInt(amount));
        return true;
    }
}
