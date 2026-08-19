package org.simdxml;

/**
 * Reusable XML value view. Plain contiguous text remains a zero-copy byte slice; entity-expanded or
 * fragmented text uses a lazy exceptional fallback. Instances are mutable and not thread-safe.
 */
final class XmlValueFlyweight implements XmlValueView {
    private static final byte[] EMPTY = new byte[0];
    private final XmlByteSlice bytes = new XmlByteSlice();
    private String materialized;
    private boolean present;

    void clear() { present = false; materialized = null; bytes.reset(EMPTY, 0, 0); }
    void wrapPlain(XmlByteSlice source) { bytes.reset(source); materialized = null; present = true; }
    void wrapPlainTrimmed(XmlByteSlice source) { bytes.resetAsciiTrimmed(source); materialized = null; present = true; }
    void wrapMaterialized(String value) { materialized = value; present = value != null; }

    @Override public boolean isPresent() { return present; }
    @Override public boolean isZeroCopy() { return present && materialized == null; }
    @Override public XmlByteSlice bytes() {
        if (!isZeroCopy()) throw new IllegalStateException("Value is absent or required XML decoding");
        return bytes;
    }
    @Override public String value() { return !present ? null : materialized != null ? materialized : bytes.decodeUtf8(); }
    @Override public String toString() { return String.valueOf(value()); }
}
