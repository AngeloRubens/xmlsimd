package org.simdxml;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

/**
 * Vector byte search over a {@link MemorySegment}.
 *
 * <p>The unrolled loop ORs the four lane masks and only locates the hit once, so the common
 * "nothing in these {@code 4 * length} bytes" iteration costs a single branch. The previous
 * version tested {@code anyTrue()} after every load, which put a data-dependent branch between
 * each vector and left nothing for the unroll to pipeline.
 *
 * <p>{@link #beatsSwar()} reports whether this backend is expected to win over
 * {@link ScalarDirectByteFinder}. On a 128-bit species each vector covers only 16 bytes against
 * SWAR's 8, while {@code fromMemorySegment} pays the checked segment access that
 * {@code DirectUnsafeAccess.getLong} avoids — measured slower at every document size, so the
 * automatic selection keeps SWAR there and this backend is used from 256 bits up.
 */
final class VectorDirectByteFinder implements DirectByteFinder {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int LENGTH = SPECIES.length();
    private static final int UNROLL = 4;
    private static final int BLOCK = LENGTH * UNROLL;
    private final ScalarDirectByteFinder scalar = new ScalarDirectByteFinder();

    /** SWAR reads 8 bytes per instruction off the unsafe path; a 128-bit vector does not repay it. */
    static boolean beatsSwar() { return LENGTH >= 32; }

    @Override public long find(MemorySegment segment, long from, long to, byte target) {
        if (to - from < LENGTH) return scalar.find(segment, from, to, target);
        long i = from;
        long blockBound = to - BLOCK;
        for (; i <= blockBound; i += BLOCK) {
            VectorMask<Byte> m0 = load(segment, i).compare(VectorOperators.EQ, target);
            VectorMask<Byte> m1 = load(segment, i + LENGTH).compare(VectorOperators.EQ, target);
            VectorMask<Byte> m2 = load(segment, i + 2L * LENGTH).compare(VectorOperators.EQ, target);
            VectorMask<Byte> m3 = load(segment, i + 3L * LENGTH).compare(VectorOperators.EQ, target);
            // One branch for the whole block; the lane search below runs at most once per call.
            if (m0.or(m1).or(m2).or(m3).anyTrue()) {
                if (m0.anyTrue()) return i + m0.firstTrue();
                if (m1.anyTrue()) return i + LENGTH + m1.firstTrue();
                if (m2.anyTrue()) return i + 2L * LENGTH + m2.firstTrue();
                return i + 3L * LENGTH + m3.firstTrue();
            }
        }
        long bound = to - LENGTH;
        for (; i <= bound; i += LENGTH) {
            VectorMask<Byte> matches = load(segment, i).compare(VectorOperators.EQ, target);
            if (matches.anyTrue()) return i + matches.firstTrue();
        }
        return scalar.find(segment, i, to, target);
    }

    private static ByteVector load(MemorySegment segment, long offset) {
        return ByteVector.fromMemorySegment(SPECIES, segment, offset, ByteOrder.nativeOrder());
    }

    @Override public String name() { return "memorysegment-vector-" + LENGTH; }
}
