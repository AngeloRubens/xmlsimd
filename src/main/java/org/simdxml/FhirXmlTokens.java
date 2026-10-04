package org.simdxml;

import java.nio.charset.StandardCharsets;

/** Primitive IDs and pre-encoded FHIR vocabulary used by the hot classifier. */
final class FhirXmlTokens {
    static final int NONE = 0, ID = 1;

    private static final byte[] B_ID = ascii("id");

    private static final long P_ID = prefix(B_ID);

    static int field(SimdXmlStreamReader reader) {
        int length = reader.localNameLength(); long prefix = reader.localNamePrefix8();
        if (length == 2 && prefix == P_ID && reader.localNameEquals(B_ID)) return ID;
        return NONE;
    }

    static boolean matches(SimdXmlStreamReader reader, int id) {
        byte[] expected = bytes(id);
        return expected != null && reader.localNameLength() == expected.length
                && reader.localNamePrefix8() == prefix(expected) && reader.localNameEquals(expected);
    }

    private static byte[] bytes(int id) {
        if (id == ID) return B_ID;
        return null;
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static long prefix(byte[] value) {
        long packed = 0; int length = Math.min(8, value.length);
        for (int i = 0; i < length; i++) packed |= (long) (value[i] & 0xff) << (i << 3);
        return packed;
    }

    private FhirXmlTokens() { }
}