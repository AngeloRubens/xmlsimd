package org.simdxml;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** JDK 9+ heap load provider selected once outside scanning loops. */
final class VarHandleLongAccess implements HeapLongAccess {
    private static final VarHandle LONGS = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    @Override public long littleEndian(byte[] input, int offset) { return (long) LONGS.get(input, offset); }
}
