package org.simdxml;

import java.io.IOException;
import java.io.OutputStream;

/** Buffered UTF-8 writer specialized for XML syntax and escaping. */
final class Utf8XmlWriter {
    private static final int INITIAL_CAPACITY = 8192;
    /* A marshaller is reused across calls: keep a grown buffer for the next document, but do not
       pin an unusually large one for the lifetime of the marshaller. */
    private static final int MAX_RETAINED_CAPACITY = 1 << 20;
    private byte[] buffer = new byte[INITIAL_CAPACITY];
    private OutputStream output;
    private int position;

    void reset(OutputStream output) { this.output = output; position = 0; }
    /** Collects the whole document in the internal buffer; read it back with {@link #toByteArray()}. */
    void resetToArray() { output = null; position = 0; }
    void finish() throws IOException { if (output != null) flush(); }
    /** One exact-size copy of the document written since {@link #resetToArray()}. */
    byte[] toByteArray() {
        byte[] document = java.util.Arrays.copyOf(buffer, position);
        if (buffer.length > MAX_RETAINED_CAPACITY) buffer = new byte[INITIAL_CAPACITY];
        position = 0;
        return document;
    }
    void ascii(char value) throws IOException { put((byte) value); }
    void raw(String value) throws IOException { writeUtf8(value, false, false); }
    void text(String value) throws IOException { writeUtf8(value, true, false); }
    void attribute(String value) throws IOException { writeUtf8(value, true, true); }

    /** Writes the decimal form of {@code value} without an intermediate String. */
    void decimal(long value) throws IOException {
        if (value == Long.MIN_VALUE) { raw("-9223372036854775808"); return; }
        if (value < 0) { put((byte) '-'); value = -value; }
        int digits = 1;
        for (long bound = 10; digits < 19 && value >= bound; bound *= 10) digits++;
        ensure(digits);
        int end = position + digits;
        for (int i = end - 1; i >= position; i--) { buffer[i] = (byte) ('0' + value % 10); value /= 10; }
        position = end;
    }

    private void writeUtf8(String value, boolean escape, boolean attribute) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escape && (c == '&' || c == '<' || attribute && c == '"')) {
                raw(c == '&' ? "&amp;" : c == '<' ? "&lt;" : "&quot;");
            } else if (c < 0x80) put((byte) c);
            else if (c < 0x800) { put((byte) (0xc0 | c >>> 6)); put((byte) (0x80 | c & 0x3f)); }
            else if (Character.isHighSurrogate(c)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new XmlBindingException("Unpaired UTF-16 surrogate in bound value");
                int codePoint = Character.toCodePoint(c, value.charAt(i));
                put((byte) (0xf0 | codePoint >>> 18)); put((byte) (0x80 | codePoint >>> 12 & 0x3f));
                put((byte) (0x80 | codePoint >>> 6 & 0x3f)); put((byte) (0x80 | codePoint & 0x3f));
            } else if (Character.isLowSurrogate(c)) throw new XmlBindingException("Unpaired UTF-16 surrogate in bound value");
            else { put((byte) (0xe0 | c >>> 12)); put((byte) (0x80 | c >>> 6 & 0x3f)); put((byte) (0x80 | c & 0x3f)); }
        }
    }

    private void put(byte value) throws IOException {
        if (position == buffer.length) makeRoom();
        buffer[position++] = value;
    }
    private void ensure(int bytes) throws IOException {
        if (buffer.length - position < bytes) makeRoom();
    }
    private void makeRoom() throws IOException {
        if (output != null) flush();
        else buffer = java.util.Arrays.copyOf(buffer, buffer.length << 1);
    }
    private void flush() throws IOException {
        if (position != 0) { output.write(buffer, 0, position); position = 0; }
    }
}
