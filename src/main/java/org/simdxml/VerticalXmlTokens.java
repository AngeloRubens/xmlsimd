package org.simdxml;

import java.nio.charset.StandardCharsets;

/** Pre-encoded protocol vocabulary and hashes; no charset conversion occurs in hot loops. */
final class VerticalXmlTokens {
    static final byte[] ENVELOPE = ascii("Envelope");
    static final byte[] HEADER = ascii("Header");
    static final byte[] BODY = ascii("Body");
    static final byte[] ACTION = ascii("Action");
    static final byte[] MESSAGE_ID = ascii("MessageID");
    static final int H_ENVELOPE = hash(ENVELOPE);
    static final int H_HEADER = hash(HEADER);
    static final int H_BODY = hash(BODY);
    static final int H_ACTION = hash(ACTION);
    static final int H_MESSAGE_ID = hash(MESSAGE_ID);

    static boolean is(SimdXmlStreamReader reader, int expectedHash, byte[] expected) {
        return reader.localNameHash() == expectedHash && reader.localNameEquals(expected);
    }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static int hash(byte[] value) {
        int hash = 0x811c9dc5;
        for (byte b : value) hash = (hash ^ (b & 0xff)) * 0x01000193;
        return hash;
    }
    private VerticalXmlTokens() { }
}
