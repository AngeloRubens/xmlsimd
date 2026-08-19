package org.simdxml;

import java.nio.charset.StandardCharsets;

/** Hot byte-oriented XML helpers; constants are encoded once at class initialization. */
final class XmlByteUtils {
    private static final HeapLongAccess LONGS = HeapLongAccesses.FASTEST;
    static final byte[] COMMENT_OPEN = ascii("<!--");
    static final byte[] COMMENT_CLOSE = ascii("-->");
    static final byte[] CDATA_OPEN = ascii("<![CDATA[");
    static final byte[] CDATA_CLOSE = ascii("]]>");
    static final byte[] DOCTYPE_OPEN = ascii("<!DOCTYPE");
    static final byte[] PI_OPEN = ascii("<?");
    static final byte[] PI_CLOSE = ascii("?>");
    static final byte[] END_TAG_OPEN = ascii("</");
    static final byte[] DECLARATION_OPEN = ascii("<!");
    static final byte[] XML_NAME = ascii("XML");
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGHS = 0x8080808080808080L;
    private static final long PACK = 0x0102040810204080L;

    static boolean starts(byte[] input, int end, int at, byte[] token) {
        if (at < 0 || at + token.length > end) return false;
        int i = 0;
        if (token.length >= 8) {
            long expected = LONGS.littleEndian(token, 0);
            if (LONGS.littleEndian(input, at) != expected) return false;
            i = 8;
        }
        for (; i < token.length; i++) if (input[at + i] != token[i]) return false;
        return true;
    }

    static boolean startsIgnoreAsciiCase(byte[] input, int end, int at, byte[] upperToken) {
        if (at < 0 || at + upperToken.length > end) return false;
        for (int i = 0; i < upperToken.length; i++) {
            int b = input[at + i] & 0xff;
            if (b >= 'a' && b <= 'z') b -= 32;
            if (b != (upperToken[i] & 0xff)) return false;
        }
        return true;
    }

    /** SWAR search on the first delimiter byte, followed by exact suffix verification. */
    static int find(byte[] input, int end, int from, byte[] needle) {
        if (needle.length == 0) return from;
        long repeated = (needle[0] & 0xffL) * ONES;
        int i = from, wordBound = Math.max(from, (end - needle.length + 1) & ~7);
        for (; i <= wordBound - 8; i += 8) {
            long word = LONGS.littleEndian(input, i);
            long high = ((word ^ repeated) - ONES) & ~(word ^ repeated) & HIGHS;
            long mask = ((high >>> 7) * PACK) >>> 56;
            while (mask != 0) {
                int candidate = i + Long.numberOfTrailingZeros(mask);
                if (starts(input, end, candidate, needle)) return candidate;
                mask &= mask - 1;
            }
        }
        outer: for (; i <= end - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (input[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private XmlByteUtils() { }
}
