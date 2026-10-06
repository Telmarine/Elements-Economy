package com.tac5studios.elementseconomy.integration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * The only class that talks to Elements: Nexus code. Never call it directly.
 * Go through {@link NexusHook}, which checks that Nexus is installed first.
 *
 * Nexus methods are looked up by name at runtime, so Economy compiles and runs
 * without Nexus, and an older Nexus without a method simply skips that feature.
 */
final class NexusBridge {

    private static final String RANKS = "com.tac5studios.elementsnexus.ranks.Ranks";

    private static MethodHandle rankOf;
    private static boolean looked;

    private NexusBridge() {}

    private static void lookUp() {
        if (looked) {
            return;
        }
        looked = true;
        MethodHandles.Lookup l = MethodHandles.publicLookup();
        // Ranks.rankOf(UUID) -> String  (present in Nexus 1.0.0)
        rankOf = find(l, RANKS, "rankOf", MethodType.methodType(String.class, UUID.class));
    }

    private static MethodHandle find(MethodHandles.Lookup l, String owner, String name, MethodType type) {
        try {
            Class<?> c = Class.forName(owner, false, NexusBridge.class.getClassLoader());
            return l.findStatic(c, name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    static String rankOf(UUID player) throws Throwable {
        lookUp();
        return rankOf == null ? "" : (String) rankOf.invokeExact(player);
    }
}
