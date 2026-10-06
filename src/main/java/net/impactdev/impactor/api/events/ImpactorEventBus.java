// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.impactdev.impactor.api.events;

import net.kyori.event.EventSubscriber;
import net.kyori.event.EventSubscription;

public final class ImpactorEventBus {
    public static ImpactorEventBus bus() { throw new AbstractMethodError(); }
    public <T extends ImpactorEvent> EventSubscription subscribe(Class<T> event, EventSubscriber<? super T> subscriber) { throw new AbstractMethodError(); }
}
