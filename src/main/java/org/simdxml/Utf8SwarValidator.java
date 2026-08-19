package org.simdxml;

/** Allocation-free UTF-8 validator with an unrolled 32-byte SWAR ASCII fast path. */
final class Utf8SwarValidator {
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final byte[] WIDTH = new byte[256];
    private static final byte[] SECOND_MIN = new byte[256];
    private static final byte[] SECOND_MAX = new byte[256];
    static {
        java.util.Arrays.fill(SECOND_MIN, (byte) 0x80);
        java.util.Arrays.fill(SECOND_MAX, (byte) 0xBF);
        java.util.Arrays.fill(WIDTH, 0, 0x80, (byte) 1);
        java.util.Arrays.fill(WIDTH, 0xC2, 0xE0, (byte) 2);
        java.util.Arrays.fill(WIDTH, 0xE0, 0xF0, (byte) 3);
        java.util.Arrays.fill(WIDTH, 0xF0, 0xF5, (byte) 4);
        SECOND_MIN[0xE0] = (byte) 0xA0; SECOND_MAX[0xED] = (byte) 0x9F;
        SECOND_MIN[0xF0] = (byte) 0x90; SECOND_MAX[0xF4] = (byte) 0x8F;
    }

    static void validate(byte[] input, int length) {
        int i = 0;
        int unrolledBound = length & ~31;
        while (i < unrolledBound) {
            long high = PortableLongs.littleEndian(input, i) | PortableLongs.littleEndian(input, i + 8)
                    | PortableLongs.littleEndian(input, i + 16) | PortableLongs.littleEndian(input, i + 24);
            if ((high & HIGH_BITS) != 0) break;
            i += 32;
        }
        while (i < length) {
            if (i + 8 <= length && (PortableLongs.littleEndian(input, i) & HIGH_BITS) == 0) {
                i += 8;
                continue;
            }
            i = validateSequence(input, length, i);
        }
    }

    static int validateSequence(byte[] input, int length, int i) {
        int b1 = input[i] & 0xff;
        int width = WIDTH[b1];
        if (width == 1) return i + 1;
        if (width == 0 || i + width > length) { invalid(i); return i; }
        int b2 = input[i + 1] & 0xff;
        if (b2 < (SECOND_MIN[b1] & 0xff) || b2 > (SECOND_MAX[b1] & 0xff)) invalid(i);
        if (width > 2 && !continuation(input[i + 2])) invalid(i);
        if (width > 3 && !continuation(input[i + 3])) invalid(i);
        return i + width;
    }
    private static boolean continuation(byte value) { return (value & 0xC0) == 0x80; }
    private static void invalid(int offset) { throw new XmlParsingException("Invalid UTF-8", offset); }
    private Utf8SwarValidator() { }
}
