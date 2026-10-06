// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package com.epherical.eights.event;

import com.epherical.octoecon.api.OctoEconomy;
import net.neoforged.bus.api.Event;

public class NeoForgeEconomyProviderPreEvent extends Event {
    public OctoEconomy<?, ?> getEconomy() { throw new AbstractMethodError(); }
    public void setEconomy(OctoEconomy<?, ?> economy) { throw new AbstractMethodError(); }
}
