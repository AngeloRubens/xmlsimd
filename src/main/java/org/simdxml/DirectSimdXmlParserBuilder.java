package org.simdxml;

/** Builder for single-thread or shareable direct-memory scanners. */
public final class DirectSimdXmlParserBuilder {
    private int maxDepth = 1024;
    private Utf8Validation utf8 = Utf8Validation.STRICT;
    public DirectSimdXmlParserBuilder withMaxDepth(int value) { if (value < 1) throw new IllegalArgumentException(); maxDepth = value; return this; }
    public DirectSimdXmlParserBuilder withUtf8Validation(Utf8Validation value) { utf8 = java.util.Objects.requireNonNull(value); return this; }
    public DirectSimdXmlParser build() { return new DirectSimdXmlParser(maxDepth, utf8); }
    public ThreadSafeDirectSimdXmlParser buildThreadSafe() { return new ThreadSafeDirectSimdXmlParser(maxDepth, utf8); }
}
