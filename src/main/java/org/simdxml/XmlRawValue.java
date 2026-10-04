package org.simdxml;

/** Java-8-compatible view used by scalar conversion without exposing the FFM implementation. */
interface XmlRawValue {
    long length();
    byte byteAt(long index);
    String decodeUtf8();
    String decodeUtf8(int from, int count);
    String decodeXmlText();
}
