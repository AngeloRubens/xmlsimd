package org.simdxml;

import java.util.Locale;

/** Runtime-dispatched UTF-8 validation, preserving operation without the Vector module. */
final class Utf8Validator {
    private static final Utf8ValidationStrategy STRATEGY = select();
    static void validate(byte[] input, int length) { STRATEGY.validate(input, length); }
    static String strategyName() { return STRATEGY.name(); }

    private static Utf8ValidationStrategy select() {
        String requested = System.getProperty("org.simdxml.utf8.strategy", "auto").toLowerCase(Locale.ROOT);
        if (requested.equals("swar") || requested.equals("scalar")) return swar();
        if (requested.equals("vector")) {
            Utf8ValidationStrategy vector = vector();
            if (vector == null) throw new IllegalStateException("Vector UTF-8 validator requested but unavailable");
            return vector;
        }
        if (!requested.equals("auto")) throw new IllegalArgumentException("Unknown UTF-8 strategy: " + requested);
        Utf8ValidationStrategy vector = vector();
        if (vector == null) return swar();
        int threshold = Math.max(0, Integer.getInteger("org.simdxml.utf8.vector.threshold", 4096));
        return new Utf8ValidationStrategy() {
            public void validate(byte[] input, int length) {
                if (length <= threshold) Utf8SwarValidator.validate(input, length); else vector.validate(input, length);
            }
            public String name() { return "adaptive-swar<=" + threshold + "/vector"; }
        };
    }
    private static Utf8ValidationStrategy swar() {
        return new Utf8ValidationStrategy() {
            public void validate(byte[] input, int length) { Utf8SwarValidator.validate(input, length); }
            public String name() { return "swar32"; }
        };
    }
    private static Utf8ValidationStrategy vector() {
        try {
            return (Utf8ValidationStrategy) Class.forName("org.simdxml.VectorUtf8Validator")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
    }
    private Utf8Validator() { }
}
