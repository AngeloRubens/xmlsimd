package org.simdxml;

/** Java 8 compatible unaligned little-endian loads; HotSpot can scalarize and combine these reads. */
final class PortableLongs {
    static long littleEndian(byte[] input, int offset) {
        return (input[offset] & 0xffL)
                | (input[offset + 1] & 0xffL) << 8
                | (input[offset + 2] & 0xffL) << 16
                | (input[offset + 3] & 0xffL) << 24
                | (input[offset + 4] & 0xffL) << 32
                | (input[offset + 5] & 0xffL) << 40
                | (input[offset + 6] & 0xffL) << 48
                | (input[offset + 7] & 0xffL) << 56;
    }
    private PortableLongs() { }
}
