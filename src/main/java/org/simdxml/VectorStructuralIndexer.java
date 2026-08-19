package org.simdxml;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/** Vector API backend, loaded reflectively so the core can run without the incubator module. */
final class VectorStructuralIndexer implements IndexingStrategy {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final byte LOW_NIBBLE_MASK = 0x0f;
    /*
     * Lemire/Langdale high/low-nibble classification. Bit 0 describes 0x2_, bit 1 0x3_.
     * XML structurals are 21,22,26,27,2f and 3c,3d,3e,3f.
     */
    private static final ByteVector LOW_TABLE = repeatedTable(new byte[]{
            0, 1, 1, 0, 0, 0, 1, 1, 0, 0, 0, 0, 2, 2, 2, 3
    });
    private static final ByteVector HIGH_TABLE = repeatedTable(new byte[]{
            0, 0, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    });

    @Override public void index(byte[] input, int length, StructuralIndex output) {
        output.clear(); int offset = 0, bound = SPECIES.loopBound(length);
        for (; offset < bound; offset += SPECIES.length()) {
            ByteVector v = ByteVector.fromArray(SPECIES, input, offset);
            output.addMask(offset, structural(v).toLong());
        }
        if (offset < length) {
            VectorMask<Byte> inRange = SPECIES.indexInRange(offset, length);
            ByteVector v = ByteVector.fromArray(SPECIES, input, offset, inRange);
            output.addMask(offset, structural(v).and(inRange).toLong());
        }
    }
    @Override public String name() { return "vector-" + SPECIES.vectorBitSize(); }
    private static VectorMask<Byte> structural(ByteVector v) {
        ByteVector lowClass = LOW_TABLE.rearrange(v.and(LOW_NIBBLE_MASK).toShuffle());
        ByteVector highNibbles = v.lanewise(VectorOperators.LSHR, 4).and(LOW_NIBBLE_MASK);
        ByteVector highClass = HIGH_TABLE.rearrange(highNibbles.toShuffle());
        return lowClass.and(highClass).compare(VectorOperators.NE, 0);
    }
    private static ByteVector repeatedTable(byte[] table) {
        byte[] lanes = new byte[SPECIES.length()];
        for (int i = 0; i < lanes.length; i++) lanes[i] = table[i & 15];
        return ByteVector.fromArray(SPECIES, lanes, 0);
    }
}
