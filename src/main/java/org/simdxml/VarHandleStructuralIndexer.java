package org.simdxml;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** JDK 9+ unaligned SWAR loads; packaged outside the Java 8 core. */
final class VarHandleStructuralIndexer implements IndexingStrategy {
    private static final VarHandle LONGS = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    @Override public void index(byte[] input, int length, StructuralIndex output) {
        output.clear(); int offset = 0, unrolledBound = length & ~31;
        for (; offset < unrolledBound; offset += 32) {
            long mask = ScalarStructuralIndexer.structuralMask((long) LONGS.get(input, offset))
                    | ScalarStructuralIndexer.structuralMask((long) LONGS.get(input, offset + 8)) << 8
                    | ScalarStructuralIndexer.structuralMask((long) LONGS.get(input, offset + 16)) << 16
                    | ScalarStructuralIndexer.structuralMask((long) LONGS.get(input, offset + 24)) << 24;
            output.addMask(offset, mask);
        }
        int wordBound = length & ~7;
        for (; offset < wordBound; offset += 8)
            output.addMask(offset, ScalarStructuralIndexer.structuralMask((long) LONGS.get(input, offset)));
        long tail = 0;
        for (int lane = 0; offset + lane < length; lane++) if (structural(input[offset + lane])) tail |= 1L << lane;
        output.addMask(offset, tail);
    }
    @Override public String name() { return "varhandle-swar64"; }
    private static boolean structural(byte value) {
        return value == '<' || value == '>' || value == '/' || value == '=' || value == '\''
                || value == '"' || value == '&' || value == '?' || value == '!';
    }
}
