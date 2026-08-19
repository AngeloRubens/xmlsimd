package org.simdxml;

/**
 * Shared parser policy and immutable configuration.
 *
 * <p>Memory access and scanning deliberately remain in concrete parsers so their
 * byte/word loops stay monomorphic and optimizable by the JIT.</p>
 */
public abstract class AbstractXmlParser {
    protected final int maxDepth;
    protected final Utf8Validation utf8Validation;

    protected AbstractXmlParser(int maxDepth, Utf8Validation utf8Validation) {
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be positive");
        this.maxDepth = maxDepth;
        this.utf8Validation = java.util.Objects.requireNonNull(utf8Validation, "utf8Validation");
    }

    public final int maxDepth() { return maxDepth; }
    public final Utf8Validation utf8Validation() { return utf8Validation; }

    protected static Utf8Validation configuredUtf8Validation() {
        String value = System.getProperty("org.simdxml.utf8.validation", "strict");
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if ("strict".equals(normalized) || "on".equals(normalized) || "true".equals(normalized))
            return Utf8Validation.STRICT;
        if ("none".equals(normalized) || "off".equals(normalized) || "false".equals(normalized))
            return Utf8Validation.NONE;
        throw new IllegalArgumentException("Unknown UTF-8 validation strategy: " + value);
    }
}
