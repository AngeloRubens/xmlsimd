package org.simdxml;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

/**
 * Optional SWAR backend using raw heap loads. It is loaded reflectively and is never required for
 * normal operation. Every raw load is guarded by a proven eight-byte in-bounds loop limit.
 */
@SuppressWarnings("removal")
final class UnsafeStructuralIndexer implements IndexingStrategy {
    private static final Unsafe UNSAFE = acquireUnsafe();
    private static final long BYTE_BASE = UNSAFE.arrayBaseOffset(byte[].class);
    private static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGHS = 0x8080808080808080L;
    private static final long LOW_SEVENS = 0x7f7f7f7f7f7f7f7fL;
    private static final long PACK_HIGH_BITS = 0x0102040810204080L;
    private static final long LT = repeat((byte)'<');
    private static final long GT = repeat((byte)'>');
    private static final long SLASH = repeat((byte)'/');
    private static final long EQUALS = repeat((byte)'=');
    private static final long APOSTROPHE = repeat((byte)'\'');
    private static final long QUOTE = repeat((byte)'"');
    private static final long AMPERSAND = repeat((byte)'&');
    private static final long QUESTION = repeat((byte)'?');
    private static final long BANG = repeat((byte)'!');
    private static final boolean[] STRUCTURAL = createLookup();

    @Override public void index(byte[] input, int length, StructuralIndex output) {
        output.clear();
        int offset = 0;
        int unrolledBound = length & ~31;
        for (; offset < unrolledBound; offset += 32) {
            long address = BYTE_BASE + offset;
            long word0 = littleEndian(UNSAFE.getLong(input, address));
            long word1 = littleEndian(UNSAFE.getLong(input, address + 8));
            long word2 = littleEndian(UNSAFE.getLong(input, address + 16));
            long word3 = littleEndian(UNSAFE.getLong(input, address + 24));
            long mask = structuralMask(word0)
                    | structuralMask(word1) << 8
                    | structuralMask(word2) << 16
                    | structuralMask(word3) << 24;
            output.addMask(offset, mask);
        }
        int wordBound = length & ~7;
        for (; offset < wordBound; offset += 8)
            output.addMask(offset, structuralMask(littleEndian(UNSAFE.getLong(input, BYTE_BASE + offset))));
        long tail = 0;
        for (int lane = 0; offset + lane < length; lane++)
            if (STRUCTURAL[input[offset + lane] & 0xff]) tail |= 1L << lane;
        output.addMask(offset, tail);
    }

    @Override public String name() { return "unsafe-swar64"; }
    private static long littleEndian(long word) { return BIG_ENDIAN ? Long.reverseBytes(word) : word; }
    private static long repeat(byte value) { return (value & 0xffL) * ONES; }
    private static long zeroByteHighBits(long value) {
        return ~(((value & LOW_SEVENS) + LOW_SEVENS) | value | LOW_SEVENS) & HIGHS;
    }
    private static long structuralMask(long word) {
        long highBits = zeroByteHighBits(word ^ LT) | zeroByteHighBits(word ^ GT)
                | zeroByteHighBits(word ^ SLASH) | zeroByteHighBits(word ^ EQUALS)
                | zeroByteHighBits(word ^ APOSTROPHE) | zeroByteHighBits(word ^ QUOTE)
                | zeroByteHighBits(word ^ AMPERSAND) | zeroByteHighBits(word ^ QUESTION)
                | zeroByteHighBits(word ^ BANG);
        return ((highBits >>> 7) * PACK_HIGH_BITS) >>> 56;
    }
    private static boolean[] createLookup() {
        boolean[] table = new boolean[256];
        table['<'] = table['>'] = table['/'] = table['='] = true;
        table['\''] = table['"'] = table['&'] = table['?'] = table['!'] = true;
        return table;
    }
    private static Unsafe acquireUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException denied) {
            throw new IllegalStateException("sun.misc.Unsafe is not accessible", denied);
        }
    }
}
