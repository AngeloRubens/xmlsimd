package org.simdxml;

import java.lang.foreign.MemorySegment;

/** Event-scoped flyweight attribute view; no map, nodes, strings, or byte copies. */
public final class DirectXmlAttributes {
    private final DirectXmlByteSlice name = new DirectXmlByteSlice();
    private final DirectXmlByteSlice value = new DirectXmlByteSlice();
    private MemorySegment segment;
    private long[] nameStarts, nameEnds, valueStarts, valueEnds;
    private int size;

    DirectXmlAttributes reset(MemorySegment segment, long[] nameStarts, long[] nameEnds,
                              long[] valueStarts, long[] valueEnds, int size) {
        this.segment = segment; this.nameStarts = nameStarts; this.nameEnds = nameEnds;
        this.valueStarts = valueStarts; this.valueEnds = valueEnds; this.size = size; return this;
    }
    public int size() { return size; }
    public DirectXmlByteSlice name(int index) { check(index); return name.reset(segment, nameStarts[index], nameEnds[index]); }
    public DirectXmlByteSlice rawValue(int index) { check(index); return value.reset(segment, valueStarts[index], valueEnds[index]); }
    public int findAscii(byte[] expectedName) {
        for (int i = 0; i < size; i++) if (name.reset(segment, nameStarts[i], nameEnds[i]).equalsAscii(expectedName)) return i;
        return -1;
    }
    public DirectXmlByteSlice rawValueAscii(byte[] expectedName) {
        int index = findAscii(expectedName); return index < 0 ? null : rawValue(index);
    }
    private void check(int index) { if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index); }
}
