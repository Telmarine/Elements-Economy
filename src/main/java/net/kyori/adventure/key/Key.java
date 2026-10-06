// STUB for compiling only. Excluded from the jar (build.gradle); the real class comes from the mod at runtime.
package net.kyori.adventure.key;

public interface Key {
    static Key key(String namespace, String value) { throw new AbstractMethodError(); }
    static Key key(String string) { throw new AbstractMethodError(); }
    String namespace();
    String value();
    String asString();
}
