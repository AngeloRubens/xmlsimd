package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/** Reader-owned zero-copy flyweight over heap or off-heap memory. */
public final class DirectXmlByteSlice implements XmlRawValue, XmlByteName {
    private MemorySegment segment = MemorySegment.NULL;
    private long offset;
    private long length;
    /* One scan for ':' per reset, not one per accessor: the binder asks four questions per name. */
    private long localOffset = -1;
    /* Decoding needs the bytes on heap anyway; reuse one buffer instead of a ByteBuffer chain. */
    private byte[] scratch;
    /* Whether the run contains '&', when the scanner already knows; see the four-argument reset. */
    private static final int UNKNOWN = 0, NO_ENTITY = 1, HAS_ENTITY = 2;
    private int entityHint = UNKNOWN;

    DirectXmlByteSlice reset(MemorySegment segment, long from, long to) {
        this.segment = segment; this.offset = from; this.length = to - from; this.localOffset = -1;
        this.entityHint = UNKNOWN; return this;
    }

    /**
     * Reset with facts the scanner already established, so the slice does not rediscover them.
     * {@code localFrom} is the offset of the local part ({@code from} when the name is unqualified)
     * and {@code hasEntity} whether the run contains an {@code '&'}. Scanning for the colon was the
     * single hottest frame of the Direct path at 10.8% of samples, and the binder's own {@code '&'}
     * scan a further 5.2% — both repeating a pass the parser had just made.
     */
    DirectXmlByteSlice reset(MemorySegment segment, long from, long to, long localFrom, boolean hasEntity) {
        this.segment = segment; this.offset = from; this.length = to - from;
        this.localOffset = localFrom; this.entityHint = hasEntity ? HAS_ENTITY : NO_ENTITY; return this;
    }
    public long length() { return length; }
    /* The binder retains these bounds to defer decoding past the parser's flyweight reset. */
    MemorySegment rawSegment() { return segment; }
    long rawOffset() { return offset; }
    public byte byteAt(long index) {
        if (index < 0 || index >= length) throw new IndexOutOfBoundsException(index);
        return segment.get(ValueLayout.JAVA_BYTE, offset + index);
    }
    /** Single scan used by the binder to decide whether a segment can skip entity expansion. */
    boolean contains(byte target) {
        if (target == '&' && entityHint != UNKNOWN) return entityHint == HAS_ENTITY;
        for (long i = offset, limit = offset + length; i < limit; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, i) == target) return true;
        return false;
    }
    public boolean equalsAscii(byte[] expected) {
        if (length != expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, offset + i) != expected[i]) return false;
        return true;
    }
    /** Offset of the local part, resolved once and cached until the next reset. */
    private long local() {
        long resolved = localOffset;
        if (resolved >= 0) return resolved;
        resolved = offset;
        for (long i = offset, end = offset + length; i < end; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, i) == ':') resolved = i + 1;
        return localOffset = resolved;
    }
    public boolean localEqualsAscii(byte[] expected) {
        long local = local();
        if (offset + length - local != expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if (segment.get(ValueLayout.JAVA_BYTE, local + i) != expected[i]) return false;
        return true;
    }
    public int localNameLength() { return (int) (offset + length - local()); }
    public boolean qualified() { return local() != offset; }
    public int localNameHash() {
        int hash = 0x811c9dc5;
        for (long i = local(), end = offset + length; i < end; i++)
            hash = (hash ^ (segment.get(ValueLayout.JAVA_BYTE, i) & 0xff)) * 0x01000193;
        return hash;
    }
    private byte[] heapBytes(int count) {
        byte[] buffer = scratch;
        if (buffer == null || buffer.length < count) {
            int capacity = Math.max(64, Integer.highestOneBit(count - 1) << 1);
            scratch = buffer = new byte[capacity];
        }
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, buffer, 0, count);
        return buffer;
    }
    private int intLength() {
        if (length > Integer.MAX_VALUE - 8) throw new XmlParsingException("XML text exceeds 2GiB", 0);
        return (int) length;
    }
    /**
     * Decodes to a String with a single allocation, like the heap reader. Going through
     * {@code asSlice().asByteBuffer()} and a CharsetDecoder would allocate a segment, a buffer,
     * a CharBuffer and a char[] before the String.
     */
    public String decodeUtf8() {
        int count = intLength();
        if (count == 0) return "";
        return new String(heapBytes(count), 0, count, StandardCharsets.UTF_8);
    }
    public String decodeUtf8(int from, int count) {
        int total = intLength();
        if (from < 0 || count < 0 || from > total - count) throw new IndexOutOfBoundsException();
        if (count == 0) return "";
        return new String(heapBytes(total), from, count, StandardCharsets.UTF_8);
    }
    /** Decodes UTF-8 and expands the XML entities already validated by the direct scanner. */
    public String decodeXmlText() {
        int count = intLength();
        if (count == 0) return "";
        if (entityHint == NO_ENTITY) return new String(heapBytes(count), 0, count, StandardCharsets.UTF_8);
        byte[] bytes = heapBytes(count);
        int amp = -1;
        for (int i = 0; i < count; i++) if (bytes[i] == '&') { amp = i; break; }
        if (amp < 0) return new String(bytes, 0, count, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(count);
        int from = 0;
        while (amp >= 0) {
            if (amp > from) out.append(new String(bytes, from, amp - from, StandardCharsets.UTF_8));
            int semi = -1;
            for (int i = amp + 1; i < count; i++) if (bytes[i] == ';') { semi = i; break; }
            if (semi < 0) throw new XmlBindingException("Unclosed entity reference");
            appendEntity(out, bytes, amp + 1, semi);
            from = semi + 1;
            amp = -1;
            for (int i = from; i < count; i++) if (bytes[i] == '&') { amp = i; break; }
        }
        if (from < count) out.append(new String(bytes, from, count - from, StandardCharsets.UTF_8));
        return out.toString();
    }
    private static void appendEntity(StringBuilder out, byte[] bytes, int from, int to) {
        int length = to - from;
        if (length == 2 && bytes[from] == 'l' && bytes[from + 1] == 't') { out.append('<'); return; }
        if (length == 2 && bytes[from] == 'g' && bytes[from + 1] == 't') { out.append('>'); return; }
        if (length == 3 && bytes[from] == 'a' && bytes[from + 1] == 'm' && bytes[from + 2] == 'p') { out.append('&'); return; }
        if (length == 4 && bytes[from] == 'a' && bytes[from + 1] == 'p' && bytes[from + 2] == 'o' && bytes[from + 3] == 's') { out.append('\''); return; }
        if (length == 4 && bytes[from] == 'q' && bytes[from + 1] == 'u' && bytes[from + 2] == 'o' && bytes[from + 3] == 't') { out.append('"'); return; }
        if (length > 1 && bytes[from] == '#') {
            boolean hex = bytes[from + 1] == 'x' || bytes[from + 1] == 'X';
            int cursor = hex ? from + 2 : from + 1, radix = hex ? 16 : 10, codePoint = 0;
            if (cursor == to) throw new XmlBindingException("Malformed character reference");
            for (; cursor < to; cursor++) {
                int digit = Character.digit((char) (bytes[cursor] & 0xff), radix);
                if (digit < 0 || codePoint > (0x10ffff - digit) / radix) throw new XmlBindingException("Malformed character reference");
                codePoint = codePoint * radix + digit;
            }
            out.appendCodePoint(codePoint);
            return;
        }
        throw new XmlBindingException("Unknown entity: &" + new String(bytes, from, length, StandardCharsets.US_ASCII) + ";");
    }
    public byte[] copy() {
        byte[] out = new byte[intLength()];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, out, 0, out.length);
        return out;
    }
}
