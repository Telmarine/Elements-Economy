package com.tac5studios.elementseconomy.adapters;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection helper for currency adapters. Economy never compiles against other mods:
 * classes and methods are found by name at runtime, so a missing or changed mod only turns
 * its adapter off.
 *
 * Methods are always taken from a public type (class or interface), so package-private
 * implementations behind a public API still work.
 */
public final class Reflect {

    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    private Reflect() {}

    /** A class by name, or null when it isn't there. Doesn't run static initializers. */
    public static Class<?> cls(String name) {
        if (MISSING.contains(name)) return null;
        Class<?> c = CLASSES.get(name);
        if (c != null) return c;
        try {
            c = Class.forName(name, false, Reflect.class.getClassLoader());
            CLASSES.put(name, c);
            return c;
        } catch (ClassNotFoundException | LinkageError e) {
            MISSING.add(name);
            return null;
        }
    }

    public static boolean has(String className) {
        return cls(className) != null;
    }

    /** Calls a public static method. Throws {@link ReflectException} on any failure. */
    public static Object callStatic(String className, String method, Object... args) {
        Class<?> c = cls(className);
        if (c == null) throw new ReflectException("class " + className + " not found");
        Method m = find(c, method, args, true);
        return invoke(m, null, args);
    }

    /** Calls a public instance method, looked up on a public type of the object. */
    public static Object call(Object target, String method, Object... args) {
        if (target == null) throw new ReflectException("null target for " + method);
        Method m = find(target.getClass(), method, args, false);
        return invoke(m, target, args);
    }

    /** Creates an object with a public constructor matching the arguments. */
    public static Object create(String className, Object... args) {
        Class<?> c = cls(className);
        if (c == null) throw new ReflectException("class " + className + " not found");
        for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
            if (!matches(k.getParameterTypes(), args)) continue;
            try {
                return k.newInstance(args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new ReflectException(className + " constructor threw " + cause, cause);
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                throw new ReflectException(className + " constructor: " + e, e);
            }
        }
        throw new ReflectException("no public constructor " + className + " for " + args.length + " args");
    }

    /** True when the class has a public method with this name and argument count. */
    public static boolean hasMethod(String className, String method, int argCount) {
        Class<?> c = cls(className);
        if (c == null) return false;
        for (Method m : c.getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == argCount) return true;
        }
        return false;
    }

    /** Reads a public static field. */
    public static Object staticField(String className, String field) {
        Class<?> c = cls(className);
        if (c == null) throw new ReflectException("class " + className + " not found");
        try {
            Field f = c.getField(field);
            return f.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ReflectException(className + "." + field + ": " + e);
        }
    }

    /** Reads a public instance field. */
    public static Object field(Object target, String field) {
        try {
            Field f = target.getClass().getField(field);
            return f.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ReflectException(target.getClass().getName() + "." + field + ": " + e);
        }
    }

    /** Writes a public instance field. */
    public static void setField(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getField(field);
            f.set(target, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ReflectException(target.getClass().getName() + "." + field + ": " + e);
        }
    }

    /** An enum constant by name. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object enumConstant(String className, String name) {
        Class<?> c = cls(className);
        if (c == null || !c.isEnum()) throw new ReflectException(className + " is not an enum");
        return Enum.valueOf((Class) c, name);
    }

    // ---------- internals ----------

    private static Object invoke(Method m, Object target, Object[] args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ReflectException(m.getDeclaringClass().getSimpleName() + "." + m.getName() + " threw " + cause, cause);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new ReflectException(m.getDeclaringClass().getSimpleName() + "." + m.getName() + ": " + e, e);
        }
    }

    /** Finds a matching method declared on a public type in the class's hierarchy. */
    private static Method find(Class<?> start, String name, Object[] args, boolean wantStatic) {
        Deque<Class<?>> todo = new ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        todo.add(start);
        while (!todo.isEmpty()) {
            Class<?> c = todo.poll();
            if (c == null || !seen.add(c)) continue;
            if (Modifier.isPublic(c.getModifiers())) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(name) || !Modifier.isPublic(m.getModifiers())) continue;
                    if (Modifier.isStatic(m.getModifiers()) != wantStatic) continue;
                    if (matches(m.getParameterTypes(), args)) return m;
                }
            }
            if (c.getSuperclass() != null) todo.add(c.getSuperclass());
            for (Class<?> i : c.getInterfaces()) todo.add(i);
        }
        throw new ReflectException("no public method " + start.getSimpleName() + "." + name + " for " + args.length + " args");
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = box(types[i]);
            if (args[i] == null) {
                if (types[i].isPrimitive()) return false;
            } else if (!t.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) return t;
        if (t == int.class) return Integer.class;
        if (t == long.class) return Long.class;
        if (t == double.class) return Double.class;
        if (t == boolean.class) return Boolean.class;
        if (t == float.class) return Float.class;
        if (t == short.class) return Short.class;
        if (t == byte.class) return Byte.class;
        if (t == char.class) return Character.class;
        return t;
    }

    /** Any reflection failure. Adapters catch it and turn themselves off. */
    public static final class ReflectException extends RuntimeException {
        public ReflectException(String msg) {
            super(msg);
        }

        public ReflectException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
