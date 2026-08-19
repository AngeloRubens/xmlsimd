package org.simdxml;

/** Eight-byte Java 8 SWAR fallback requiring no Vector API, VarHandle, Unsafe or FFM. */
final class ScalarStructuralIndexer implements IndexingStrategy {
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

        /* Four independent dependency chains encourage superscalar issue and hide load latency. */
        for (; offset < unrolledBound; offset += 32) {
            long word0 = PortableLongs.littleEndian(input, offset);
            long word1 = PortableLongs.littleEndian(input, offset + 8);
            long word2 = PortableLongs.littleEndian(input, offset + 16);
            long word3 = PortableLongs.littleEndian(input, offset + 24);
            long mask = structuralMask(word0)
                    | structuralMask(word1) << 8
                    | structuralMask(word2) << 16
                    | structuralMask(word3) << 24;
            output.addMask(offset, mask);
        }

        int wordBound = length & ~7;
        for (; offset < wordBound; offset += 8) output.addMask(offset, structuralMask(PortableLongs.littleEndian(input, offset)));

        long tail = 0L;
        for (int lane = 0; offset + lane < length; lane++)
            if (STRUCTURAL[input[offset + lane] & 0xff]) tail |= 1L << lane;
        output.addMask(offset, tail);
    }
    @Override public String name() { return "scalar-swar64"; }
    private static long repeat(byte value) { return (value & 0xffL) * ONES; }
    /* Exact per-lane result: unlike (x-ONES)&~x&HIGHS, no borrow creates neighbour false positives. */
    private static long zeroByteHighBits(long value) {
        return ~(((value & LOW_SEVENS) + LOW_SEVENS) | value | LOW_SEVENS) & HIGHS;
    }

    static long structuralMask(long word) {
        long highBits = zeroByteHighBits(word ^ LT)
                | zeroByteHighBits(word ^ GT)
                | zeroByteHighBits(word ^ SLASH)
                | zeroByteHighBits(word ^ EQUALS)
                | zeroByteHighBits(word ^ APOSTROPHE)
                | zeroByteHighBits(word ^ QUOTE)
                | zeroByteHighBits(word ^ AMPERSAND)
                | zeroByteHighBits(word ^ QUESTION)
                | zeroByteHighBits(word ^ BANG);
        return ((highBits >>> 7) * PACK_HIGH_BITS) >>> 56;
    }

    private static boolean[] createLookup() {
        boolean[] table = new boolean[256];
        table['<'] = table['>'] = table['/'] = table['='] = true;
        table['\''] = table['"'] = table['&'] = table['?'] = table['!'] = true;
        return table;
    }
}
