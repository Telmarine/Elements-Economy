// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package com.epherical.octoecon.api;

import com.epherical.octoecon.api.user.FakeUser;
import com.epherical.octoecon.api.user.UniqueUser;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public interface OctoEconomy<R extends UniqueUser, F extends FakeUser> extends Economy<R, F> {
    void setServer(MinecraftServer server);
    void onPlayerJoin(UUID uuid);
    void onPlayerLeave(UUID uuid);
    void savePlayers();
    void close();
}
