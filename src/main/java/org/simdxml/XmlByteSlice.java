package org.simdxml;

import java.nio.charset.StandardCharsets;

/**
 * Reader-owned flyweight over UTF-8 document bytes. The view changes at the next reader event/reset;
 * copy or decode it before retaining data beyond that lifetime.
 */
public final class XmlByteSlice {
    private byte[] input;
    private int offset;
    private int length;

    XmlByteSlice reset(byte[] input, int from, int to) {
        this.input = input; this.offset = from; this.length = to - from;
        return this;
    }
    XmlByteSlice reset(XmlByteSlice source) {
        this.input = source.input; this.offset = source.offset; this.length = source.length;
        return this;
    }
    XmlByteSlice resetAsciiTrimmed(XmlByteSlice source) {
        int from = source.offset, to = from + source.length;
        while (from < to && asciiSpace(source.input[from])) from++;
        while (to > from && asciiSpace(source.input[to - 1])) to--;
        return reset(source.input, from, to);
    }
    boolean contains(byte value) {
        for (int i = 0; i < length; i++) if (input[offset + i] == value) return true;
        return false;
    }
    public int length() { return length; }
    public byte byteAt(int index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException(String.valueOf(index));
        return input[offset + index];
    }
    public boolean equalsAscii(byte[] expected) {
        if (length != expected.length) return false;
        for (int i = 0; i < length; i++) if (input[offset + i] != expected[i]) return false;
        return true;
    }
    public int fnv1aHash() {
        int hash = 0x811c9dc5;
        for (int i = 0; i < length; i++) hash = (hash ^ (input[offset + i] & 0xff)) * 0x01000193;
        return hash;
    }
    public String decodeUtf8() { return new String(input, offset, length, StandardCharsets.UTF_8); }
    public byte[] copy() { return java.util.Arrays.copyOfRange(input, offset, offset + length); }
    private static boolean asciiSpace(byte value) { return value == ' ' || value == '\t' || value == '\n' || value == '\r'; }
}
