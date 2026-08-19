package org.simdxml;

import java.io.IOException;
import java.io.OutputStream;

/** Buffered UTF-8 writer specialized for XML syntax and escaping. */
final class Utf8XmlWriter {
    private final byte[] buffer = new byte[8192];
    private OutputStream output;
    private int position;

    void reset(OutputStream output) { this.output = output; position = 0; }
    void finish() throws IOException { flush(); }
    void ascii(char value) throws IOException { put((byte) value); }
    void raw(String value) throws IOException { writeUtf8(value, false, false); }
    void text(String value) throws IOException { writeUtf8(value, true, false); }
    void attribute(String value) throws IOException { writeUtf8(value, true, true); }

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
        if (position == buffer.length) flush();
        buffer[position++] = value;
    }
    private void flush() throws IOException {
        if (position != 0) { output.write(buffer, 0, position); position = 0; }
    }
}
