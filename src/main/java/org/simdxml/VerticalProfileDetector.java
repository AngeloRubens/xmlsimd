package org.simdxml;

/** Bounded, allocation-free byte probe used only by VerticalProfile.AUTO. */
final class VerticalProfileDetector {
    private static final byte[] ENVELOPE = ascii("Envelope"), ISO_20022 = ascii("urn:iso:std:iso:20022"),
            PAIN = ascii("CstmrCdtTrfInitn"), PACS = ascii("FIToFICstmrCdtTrf"), CAMT = ascii("BkToCstmrStmt"),
            FHIR = ascii("http://hl7.org/fhir");
    static VerticalProfile detect(byte[] input) {
        int limit = Math.min(input.length, 8192);
        if (contains(input, limit, ISO_20022) || contains(input, limit, PAIN)
                || contains(input, limit, PACS) || contains(input, limit, CAMT)) return VerticalProfile.PAYMENTS;
        if (contains(input, limit, ENVELOPE)) return VerticalProfile.SOAP_HEALTHCARE;
        if (looksLikeHl7(input, limit)) return VerticalProfile.HL7_V2;
        if (contains(input, limit, FHIR)) return VerticalProfile.FHIR;
        return VerticalProfile.NONE;
    }
    private static boolean looksLikeHl7(byte[] input, int limit) {
        int from = limit >= 3 && (input[0] & 0xff) == 0xef
                && (input[1] & 0xff) == 0xbb && (input[2] & 0xff) == 0xbf ? 3 : 0;
        for (int i = from; i + 3 < limit; i++) {
            if (i != from && input[i - 1] != '\r' && input[i - 1] != '\n') continue;
            if (input[i] == 'M' && input[i + 1] == 'S' && input[i + 2] == 'H'
                    && input[i + 3] != '\r' && input[i + 3] != '\n') return true;
        }
        return false;
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
