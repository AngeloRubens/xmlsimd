package org.simdxml;

/**
 * Low-level, allocation-free view of the token currently exposed by an XML reader.
 * Implementations backed by bytes can provide all methods; character adapters use the
 * conservative defaults and the binder falls back to their normal name/text APIs.
 */
interface BindingCursor {
    /** True when the reader exposes raw byte names, so hash dispatch may be used instead of String keys. */
    default boolean hasByteNames() { return false; }
    default int localNameHash() { return -1; }
    default int localNameLength() { return -1; }
    default boolean localNameEquals(String name) { return false; }
    /** Byte-name comparison used by the precompiled plan; only readers with byte names implement it. */
    default boolean localNameEqualsBytes(byte[] asciiName) { return false; }
    default XmlByteSlice rawAttributeBytes(byte[] asciiName) { return null; }
    default String attribute(byte[] asciiName) { return null; }
    default XmlByteSlice rawTextBytes() { return null; }
    default XmlByteSlice rawAttributeBytes(String name) { return null; }
}
