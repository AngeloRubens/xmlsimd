package org.simdxml;

/** Read-only value exposed by a scoped vertical flyweight callback. */
public interface XmlValueView {
    boolean isPresent();
    boolean isZeroCopy();
    XmlByteSlice bytes();
    String value();
}
