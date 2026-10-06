package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * MCreator mods that keep the balance as a double in their player-variables attachment:
 * M Economy (Balance), Kuro's Economy (bal), Mezzo's Money (bankaccount). Online players only.
 */
final class McreatorVariablesAdapter extends ReflectBackend {

    private final String holder;
    private final String field;
    private final boolean syncNow;

    private McreatorVariablesAdapter(String modId, String name, String holder, String field, boolean syncNow, String coins) {
        super(new ExternalCurrency(modId, "bank", name, 2, EnumSet.noneOf(Capability.class), coins, null));
        this.holder = holder;
        this.field = field;
        this.syncNow = syncNow;
    }

    static McreatorVariablesAdapter mEconomy() {
        return new McreatorVariablesAdapter("currency", "Dollars", "currency.network.CurrencyModVariables", "Balance", true, "currency");
    }

    static McreatorVariablesAdapter kuronomy() {
        return new McreatorVariablesAdapter("kuronomy", "Dollars", "kuronomy.network.KuronomyModVariables", "bal", false, "kuronomy");
    }

    static McreatorVariablesAdapter mezzos() {
        return new McreatorVariablesAdapter("mezzos_money_mod", "Euro", "net.mcreator.mezzosmoneymod.network.MezzosMoneyModModVariables",
                "bankaccount", false, "mezzos_money_mod");
    }

    private Object vars(ServerPlayer p) {
        Object supplier = Reflect.staticField(holder, "PLAYER_VARIABLES");
        Object type = supplier instanceof Supplier<?> s ? s.get() : Reflect.call(supplier, "get");
        return p.getData((AttachmentType<?>) type);
    }

    @Override
    protected boolean ready() {
        return Reflect.has(holder) && Reflect.staticField(holder, "PLAYER_VARIABLES") != null;
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        return p == null ? null : fromDouble(Reflect.field(vars(p), field));
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        Object v = vars(p);
        Reflect.setField(v, field, toDouble(amount));
        if (syncNow) Reflect.call(v, "syncPlayerVariables", p);
        else Reflect.call(v, "markSyncDirty");
        return true;
    }
}
