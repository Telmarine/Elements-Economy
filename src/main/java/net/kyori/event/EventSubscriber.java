// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.kyori.event;

@FunctionalInterface
public interface EventSubscriber<E> {
    void on(E event) throws Throwable;
}
