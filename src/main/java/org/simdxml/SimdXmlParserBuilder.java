package org.simdxml;

/** Builder selecting either the lock-free single-thread core or a ThreadLocal multithread facade. */
public final class SimdXmlParserBuilder {
    private int capacity = 34 * 1024 * 1024;
    private int maxDepth = 1024;
    private VerticalProfile verticalProfile = VerticalProfile.configured();
    private Utf8Validation utf8Validation = Utf8Validation.STRICT;
    private int tinyDocumentThreshold = -1;

    public SimdXmlParserBuilder withCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity; return this;
    }
    public SimdXmlParserBuilder withMaxDepth(int maxDepth) {
        if (maxDepth < 1) throw new IllegalArgumentException("maxDepth must be positive");
        this.maxDepth = maxDepth; return this;
    }
    public SimdXmlParserBuilder withVerticalOptimizations(boolean enabled) {
        this.verticalProfile = enabled ? VerticalProfile.AUTO : VerticalProfile.NONE; return this;
    }
    public SimdXmlParserBuilder withVerticalProfile(VerticalProfile profile) {
        this.verticalProfile = java.util.Objects.requireNonNull(profile, "profile"); return this;
    }
    public SimdXmlParserBuilder withUtf8Validation(Utf8Validation validation) {
        this.utf8Validation = java.util.Objects.requireNonNull(validation, "validation"); return this;
    }

    /**
     * Documents at or below this many bytes skip the structural index and scan directly. Overrides
     * {@code -Dorg.simdxml.tiny.threshold}, whose default is 4096; exposed so an A/B benchmark can
     * vary the threshold without one JVM per value.
     */
    public SimdXmlParserBuilder withTinyDocumentThreshold(int threshold) {
        if (threshold < 0) throw new IllegalArgumentException("threshold must not be negative");
        this.tinyDocumentThreshold = threshold; return this;
    }

    /** Builds the fastest core instance; it is intentionally not thread safe. */
    public SimdXmlParser build() {
        return new SimdXmlParser(capacity, maxDepth, verticalProfile, utf8Validation, tinyDocumentThreshold);
    }

    /** Builds a shareable facade with one lock-free parser, reader, and symbol table per thread. */
    public ThreadSafeSimdXmlParser buildThreadSafe() {
        return new ThreadSafeSimdXmlParser(capacity, maxDepth, verticalProfile, utf8Validation);
    }
}
