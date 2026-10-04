package org.simdxml;

/** Portable byte-name view shared by heap and FFM binding dispatch. */
interface XmlByteName {
    int localNameHash();
    int localNameLength();
    boolean localEqualsAscii(byte[] expected);
}
