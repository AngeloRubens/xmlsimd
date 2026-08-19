package org.simdxml;

/** Bounded, allocation-free byte probe used only by VerticalProfile.AUTO. */
final class VerticalProfileDetector {
    private static final byte[] ENVELOPE = ascii("Envelope"), ISO_20022 = ascii("urn:iso:std:iso:20022"),
            PAIN = ascii("CstmrCdtTrfInitn"), PACS = ascii("FIToFICstmrCdtTrf"), CAMT = ascii("BkToCstmrStmt");
    static VerticalProfile detect(byte[] input) {
        int limit = Math.min(input.length, 8192);
        if (contains(input, limit, ISO_20022) || contains(input, limit, PAIN)
                || contains(input, limit, PACS) || contains(input, limit, CAMT)) return VerticalProfile.PAYMENTS;
        if (contains(input, limit, ENVELOPE)) return VerticalProfile.SOAP_HEALTHCARE;
        return VerticalProfile.NONE;
    }
    private static boolean contains(byte[] input, int limit, byte[] token) {
        int last = limit - token.length;
        for (int i = 0; i <= last; i++) {
            if (input[i] != token[0]) continue;
            int j = 1; while (j < token.length && input[i + j] == token[j]) j++;
            if (j == token.length) return true;
        }
        return false;
    }
    private static byte[] ascii(String value) { return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    private VerticalProfileDetector() { }
}
