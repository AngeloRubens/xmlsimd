package org.simdxml;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

final class VectorDirectByteFinder implements DirectByteFinder {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int VECTOR_THRESHOLD = SPECIES.length() * 2;
    private final ScalarDirectByteFinder scalar = new ScalarDirectByteFinder();

    @Override public long find(MemorySegment segment, long from, long to, byte target) {
        if (to - from < VECTOR_THRESHOLD) return scalar.find(segment, from, to, target);
        long i = from, bound = to - SPECIES.length();
        for (; i <= bound; i += SPECIES.length()) {
            ByteVector bytes = ByteVector.fromMemorySegment(SPECIES, segment, i, ByteOrder.nativeOrder());
            VectorMask<Byte> matches = bytes.compare(VectorOperators.EQ, target);
            if (matches.anyTrue()) return i + matches.firstTrue();
        }
        return scalar.find(segment, i, to, target);
    }
    @Override public String name() { return "memorysegment-vector-" + SPECIES.length(); }
}
