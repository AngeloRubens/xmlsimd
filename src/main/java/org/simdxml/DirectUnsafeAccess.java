package org.simdxml;

import sun.misc.Unsafe;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;

/** Chronicle-style native address access while the owning MemorySegment remains alive. */
final class DirectUnsafeAccess {
    static final Unsafe UNSAFE = load();
    static byte getByte(MemorySegment segment, long offset) {
        return segment.isNative() && UNSAFE != null ? UNSAFE.getByte(segment.address() + offset)
                : segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, offset);
    }
    static long getLong(MemorySegment segment, long offset) {
        return segment.isNative() && UNSAFE != null ? UNSAFE.getLong(segment.address() + offset)
                : segment.get(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, offset);
    }
    private static Unsafe load() {
        try { Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (Unsafe) field.get(null); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private DirectUnsafeAccess() { }
}
