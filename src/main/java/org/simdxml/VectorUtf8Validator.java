package org.simdxml;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/** Vector bulk ASCII classifier with strict canonical UTF-8 slow paths. */
final class VectorUtf8Validator implements Utf8ValidationStrategy {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;

    @Override public void validate(byte[] input, int length) {
        int offset = 0;
        while (offset <= length - SPECIES.length()) {
            ByteVector bytes = ByteVector.fromArray(SPECIES, input, offset);
            VectorMask<Byte> nonAscii = bytes.compare(VectorOperators.LT, (byte) 0);
            if (!nonAscii.anyTrue()) { offset += SPECIES.length(); continue; }
            offset += nonAscii.firstTrue();
            offset = Utf8SwarValidator.validateSequence(input, length, offset);
        }
        while (offset < length) offset = Utf8SwarValidator.validateSequence(input, length, offset);
    }
    @Override public String name() { return "vector-direct"; }
    VectorUtf8Validator() { }
}
