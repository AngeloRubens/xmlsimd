package org.simdxml;

import java.nio.charset.StandardCharsets;

/** Primitive IDs and pre-encoded ISO 20022 vocabulary used by the hot classifier. */
final class PaymentXmlTokens {
    static final int NONE = 0, MSG_ID = 1, NB_OF_TXS = 2, CTRL_SUM = 3, IBAN = 4, BICFI = 5, BIC = 6;
    static final int PAIN_001 = 101, PAIN_002 = 102, PACS_008 = 103, PACS_002 = 104, CAMT_053 = 105, CAMT_054 = 106;

    private static final byte[] B_MSG_ID = ascii("MsgId"), B_NB_OF_TXS = ascii("NbOfTxs"),
            B_CTRL_SUM = ascii("CtrlSum"), B_IBAN = ascii("IBAN"), B_BICFI = ascii("BICFI"), B_BIC = ascii("BIC"),
            B_PAIN_001 = ascii("CstmrCdtTrfInitn"), B_PAIN_002 = ascii("CstmrPmtStsRpt"),
            B_PACS_008 = ascii("FIToFICstmrCdtTrf"), B_PACS_002 = ascii("FIToFIPmtStsRpt"),
            B_CAMT_053 = ascii("BkToCstmrStmt"), B_CAMT_054 = ascii("BkToCstmrDbtCdtNtfctn");
    private static final long P_MSG_ID = prefix(B_MSG_ID), P_NB_OF_TXS = prefix(B_NB_OF_TXS),
            P_CTRL_SUM = prefix(B_CTRL_SUM), P_IBAN = prefix(B_IBAN), P_BICFI = prefix(B_BICFI), P_BIC = prefix(B_BIC),
            P_PAIN_001 = prefix(B_PAIN_001), P_PAIN_002 = prefix(B_PAIN_002),
            P_PACS_008 = prefix(B_PACS_008), P_PACS_002 = prefix(B_PACS_002),
            P_CAMT_053 = prefix(B_CAMT_053), P_CAMT_054 = prefix(B_CAMT_054);
    private static final long S_PAIN_001 = suffix(B_PAIN_001), S_PAIN_002 = suffix(B_PAIN_002),
            S_PACS_008 = suffix(B_PACS_008), S_PACS_002 = suffix(B_PACS_002),
            S_CAMT_053 = suffix(B_CAMT_053), S_CAMT_054 = suffix(B_CAMT_054);

    static int field(SimdXmlStreamReader reader) {
        int length = reader.localNameLength(); long prefix = reader.localNamePrefix8();
        if (length == 5 && prefix == P_MSG_ID && reader.localNameEquals(B_MSG_ID)) return MSG_ID;
        if (length == 7 && prefix == P_NB_OF_TXS && reader.localNameEquals(B_NB_OF_TXS)) return NB_OF_TXS;
        if (length == 7 && prefix == P_CTRL_SUM && reader.localNameEquals(B_CTRL_SUM)) return CTRL_SUM;
        if (length == 4 && prefix == P_IBAN && reader.localNameEquals(B_IBAN)) return IBAN;
        if (length == 5 && prefix == P_BICFI && reader.localNameEquals(B_BICFI)) return BICFI;
        if (length == 3 && prefix == P_BIC && reader.localNameEquals(B_BIC)) return BIC;
        return NONE;
    }

    static int message(SimdXmlStreamReader reader) {
        int length = reader.localNameLength(); long prefix = reader.localNamePrefix8(), suffix = reader.localNameSuffix8();
        if (length == B_PAIN_001.length && prefix == P_PAIN_001 && suffix == S_PAIN_001 && reader.localNameEquals(B_PAIN_001)) return PAIN_001;
        if (length == B_PAIN_002.length && prefix == P_PAIN_002 && suffix == S_PAIN_002 && reader.localNameEquals(B_PAIN_002)) return PAIN_002;
        if (length == B_PACS_008.length && prefix == P_PACS_008 && suffix == S_PACS_008 && reader.localNameEquals(B_PACS_008)) return PACS_008;
        if (length == B_PACS_002.length && prefix == P_PACS_002 && suffix == S_PACS_002 && reader.localNameEquals(B_PACS_002)) return PACS_002;
        if (length == B_CAMT_053.length && prefix == P_CAMT_053 && suffix == S_CAMT_053 && reader.localNameEquals(B_CAMT_053)) return CAMT_053;
        if (length == B_CAMT_054.length && prefix == P_CAMT_054 && suffix == S_CAMT_054 && reader.localNameEquals(B_CAMT_054)) return CAMT_054;
        return NONE;
    }

    static boolean matches(SimdXmlStreamReader reader, int id) {
        byte[] expected = bytes(id);
        return expected != null && reader.localNameLength() == expected.length
                && reader.localNamePrefix8() == prefix(expected) && reader.localNameEquals(expected);
    }

    static String messageName(int id) {
        if (id == PAIN_001) return "CstmrCdtTrfInitn"; if (id == PAIN_002) return "CstmrPmtStsRpt";
        if (id == PACS_008) return "FIToFICstmrCdtTrf"; if (id == PACS_002) return "FIToFIPmtStsRpt";
        if (id == CAMT_053) return "BkToCstmrStmt"; if (id == CAMT_054) return "BkToCstmrDbtCdtNtfctn";
        return null;
    }

    private static byte[] bytes(int id) {
        if (id == MSG_ID) return B_MSG_ID; if (id == NB_OF_TXS) return B_NB_OF_TXS;
        if (id == CTRL_SUM) return B_CTRL_SUM; if (id == IBAN) return B_IBAN;
        if (id == BICFI) return B_BICFI; if (id == BIC) return B_BIC;
        return null;
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static long prefix(byte[] value) {
        long packed = 0; int length = Math.min(8, value.length);
        for (int i = 0; i < length; i++) packed |= (long) (value[i] & 0xff) << (i << 3);
        return packed;
    }
    private static long suffix(byte[] value) {
        long packed = 0; int length = Math.min(8, value.length), start = value.length - length;
        for (int i = 0; i < length; i++) packed |= (long) (value[start + i] & 0xff) << (i << 3);
        return packed;
    }
    private PaymentXmlTokens() { }
}
