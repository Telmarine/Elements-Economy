// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.economy.events;

import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.events.ImpactorEvent;
import net.impactdev.impactor.api.platform.plugins.PluginMetadata;

import java.util.function.Supplier;

public interface SuggestEconomyServiceEvent extends ImpactorEvent {
    void suggest(PluginMetadata suggestor, Supplier<EconomyService> service, int priority);
}
