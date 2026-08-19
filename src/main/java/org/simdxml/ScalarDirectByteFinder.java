package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class ScalarDirectByteFinder implements DirectByteFinder {
    private static final long ONES = 0x0101010101010101L, HIGHS = 0x8080808080808080L;
    @Override public long find(MemorySegment segment, long from, long to, byte target) {
        long repeated = (target & 0xffL) * ONES, i = from;
        for (; i + 8 <= to; i += 8) {
            long x = DirectUnsafeAccess.getLong(segment, i) ^ repeated;
            if (((x - ONES) & ~x & HIGHS) != 0)
                for (int lane = 0; lane < 8; lane++)
                    if (DirectUnsafeAccess.getByte(segment, i + lane) == target) return i + lane;
        }
        for (; i < to; i++) if (DirectUnsafeAccess.getByte(segment, i) == target) return i;
        return to;
    }
    @Override public String name() { return "memorysegment-swar64"; }
}
