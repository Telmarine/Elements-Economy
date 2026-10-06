package com.tac5studios.elementseconomy.adapters;

import com.tac5studios.elementseconomy.config.EconomyConfig;
import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/**
 * SDM Economy. Two lines exist on 1.21.1:
 *  - 2.x (mod id sdmeconomy): CurrencyPlayerData.SERVER, by UUID, doubles. Works offline.
 *  - 1.6.x (mod id sdm_economy): CurrencyHelper.getMoney/setMoney(Player, id, long). Online only.
 * The currency name comes from economy.toml (default "basic_money").
 */
final class SdmEconomyAdapter extends ReflectBackend {

    private static final String NEW_DATA = "net.sixik.sdmeconomy.economyData.CurrencyPlayerData";
    private static final String OLD_HELPER = "net.sixik.sdm_economy.api.CurrencyHelper";

    private final boolean newApi;

    private SdmEconomyAdapter(boolean newApi) {
        super(new ExternalCurrency(newApi ? "sdmeconomy" : "sdm_economy", "money", "SDM Money", 0,
                newApi ? EnumSet.of(Capability.OFFLINE_READ, Capability.OFFLINE_WRITE) : EnumSet.noneOf(Capability.class),
                null, null));
        this.newApi = newApi;
    }

    static SdmEconomyAdapter newer() {
        return new SdmEconomyAdapter(true);
    }

    static SdmEconomyAdapter older() {
        return new SdmEconomyAdapter(false);
    }

    private static String currencyName() {
        return EconomyConfig.SDM_CURRENCY.get();
    }

    @Override
    protected boolean ready() {
        if (newApi) return Reflect.has(NEW_DATA) && Reflect.staticField(NEW_DATA, "SERVER") != null;
        return Reflect.hasMethod(OLD_HELPER, "getMoney", 2);
    }

    @Override
    protected BigInteger read(UUID uuid) {
        if (newApi) {
            Object data = Reflect.staticField(NEW_DATA, "SERVER");
            Object struct = Reflect.call(data, "getBalance", uuid, currencyName());
            Object value = Reflect.field(struct, "value");
            return fromDouble(value);
        }
        ServerPlayer p = online(uuid);
        return p == null ? null : fromLong(Reflect.callStatic(OLD_HELPER, "getMoney", p, currencyName()));
    }

    @Override
    protected boolean write(UUID uuid, BigInteger amount) {
        if (newApi) {
            Object data = Reflect.staticField(NEW_DATA, "SERVER");
            Object code = Reflect.call(data, "setCurrencyValue", uuid, currencyName(), toDouble(amount));
            return code != null && "SUCCESS".equals(code.toString());
        }
        ServerPlayer p = online(uuid);
        if (p == null) return false;
        Reflect.callStatic(OLD_HELPER, "setMoney", p, currencyName(), toLong(amount));
        return true;
    }
}
