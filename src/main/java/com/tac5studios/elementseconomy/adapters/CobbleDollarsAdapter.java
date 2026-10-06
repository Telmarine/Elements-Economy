package com.tac5studios.elementseconomy.adapters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tac5studios.elementsvault.Capability;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.UUID;

/**
 * CobbleDollars 2.x. Online: PlayerExtensionKt.getCobbleDollars / setCobbleDollars (BigInteger on the player).
 * Offline: read-only, from the mod's own account file (last saved balance + offline earnings).
 */
final class CobbleDollarsAdapter extends ReflectBackend {

    private static final String EXT = "fr.harmex.cobbledollars.common.utils.extensions.PlayerExtensionKt";

    CobbleDollarsAdapter() {
        super(new ExternalCurrency("cobbledollars", "cobbledollars", "CobbleDollars", 0,
                EnumSet.of(Capability.OFFLINE_READ), null, null));
    }

    @Override
    protected boolean ready() {
        return Reflect.hasMethod(EXT, "getCobbleDollars", 1) && Reflect.hasMethod(EXT, "setCobbleDollars", 2);
    }

    @Override
    protected BigInteger read(UUID id) {
        ServerPlayer p = online(id);
        if (p != null) return (BigInteger) Reflect.callStatic(EXT, "getCobbleDollars", p);
        return offlineRead(id);
    }

    @Override
    protected boolean write(UUID id, BigInteger amount) {
        ServerPlayer p = online(id);
        if (p == null) return false;
        Reflect.callStatic(EXT, "setCobbleDollars", p, amount);
        return true;
    }

    /** Last saved balance plus earnings waiting for the next login. Null when there's no file. */
    private BigInteger offlineRead(UUID id) {
        File f = (File) Reflect.callStatic(EXT, "getAccountFile", id, server());
        if (f == null || !f.exists()) return null;
        try (Reader r = new FileReader(f)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            BigInteger bal = o.has("cobbledollars") ? o.get("cobbledollars").getAsBigInteger() : BigInteger.ZERO;
            BigInteger extra = o.has("offline_earnings") ? o.get("offline_earnings").getAsBigInteger() : BigInteger.ZERO;
            return bal.add(extra);
        } catch (Exception e) {
            return null;
        }
    }
}
