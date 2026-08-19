package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/** Reader-owned zero-copy flyweight over heap or off-heap memory. */
public final class DirectXmlByteSlice {
    private MemorySegment segment = MemorySegment.NULL;
    private long offset;
    private long length;

    DirectXmlByteSlice reset(MemorySegment segment, long from, long to) {
        this.segment = segment; this.offset = from; this.length = to - from; return this;
    }
    public long length() { return length; }
    public byte byteAt(long index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException(index);
        return segment.get(ValueLayout.JAVA_BYTE, offset + index);
    }
    public boolean equalsAscii(byte[] expected) {
        if (length != expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, offset + i) != expected[i]) return false;
        return true;
    }
    public boolean localEqualsAscii(byte[] expected) {
        long local = offset;
        for (long i = offset; i < offset + length; i++) if (segment.get(ValueLayout.JAVA_BYTE, i) == ':') local = i + 1;
        if (offset + length - local != expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, local + i) != expected[i]) return false;
        return true;
    }
    public String decodeUtf8() {
        return StandardCharsets.UTF_8.decode(segment.asSlice(offset, length).asByteBuffer()).toString();
    }
    public byte[] copy() { return segment.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE); }
}
