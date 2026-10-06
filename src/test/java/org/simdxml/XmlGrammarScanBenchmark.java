package org.simdxml;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Benchmarks SWAR candidate classification followed by an XML-context state machine. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class XmlGrammarScanBenchmark {
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final long LOW_BITS = 0x0101010101010101L;
    private static final char[] CANDIDATES = "<>&;\"'/?=![]- \t\r\n".toCharArray();
    private static final byte[] CLASS = classes();

    private static final int TEXT = 0, TAG = 1, SINGLE_QUOTE = 2, DOUBLE_QUOTE = 3;
    private static final int COMMENT = 4, CDATA = 5, PI = 6, DECL = 7;
    private static final int DECL_SINGLE_QUOTE = 8, DECL_DOUBLE_QUOTE = 9;
    private static final int ENTITY_TEXT = 10, ENTITY_SINGLE_QUOTE = 11, ENTITY_DOUBLE_QUOTE = 12;

    @Param({"4096", "65536"}) public int targetBytes;
    @Param({"ascii", "mixed", "unicode"}) public String content;

    private byte[] input;

    @Setup(Level.Trial)
    public void setup() {
        String unit = switch (content) {
            case "ascii" -> "<entry id=\"184\"><name>XML scanner</name><count>12345</count></entry>";
            case "mixed" -> "<entry id=\"184\"><name>XML café 東京</name><count>12345</count></entry>";
            case "unicode" -> "<entry id=\"184\"><name>café 東京 🧪 &amp; tea</name><count>12345</count></entry>";
            default -> throw new IllegalArgumentException(content);
        };
        StringBuilder document = new StringBuilder(targetBytes + 128)
                .append("<?xml version=\"1.0\"?><feed>");
        while (document.length() < targetBytes) {
            document.append(unit).append("<!-- <ignored attr='x'> &amp; -->")
                    .append("<![CDATA[<raw>&text]]>");
        }
        document.append("</feed>");
        input = document.toString().getBytes(StandardCharsets.UTF_8);
        long scalar = scanScalar(input, 0, input.length);
        long swar = scanSwar(input, 0, input.length);
        if (scalar != swar) throw new IllegalStateException("SWAR XML scanner disagrees with scalar scanner");
    }

    @Benchmark
    public long scalarXmlGrammar() {
        return scanScalar(input, 0, input.length);
    }

    @Benchmark
    public long swarXmlGrammar() {
        return scanSwar(input, 0, input.length);
    }

    static long scanScalar(byte[] source, int offset, int length) {
        int end = offset + length, mode = TEXT, subsetDepth = 0, hash = 1;
        for (int i = offset; i < end; i++) {
            int type = CLASS[source[i] & 0xff] & 0xff;
            if (type != 0) {
                long next = step(source, i, mode, subsetDepth, hash, offset);
                mode = (int) next & 0xff;
                subsetDepth = (int) (next >>> 8) & 0xff;
                hash = (int) (next >>> 16);
            }
        }
        return ((long) mode << 56) | ((long) subsetDepth << 48) | (hash & 0xffff_ffffL);
    }

    static long scanSwar(byte[] source, int offset, int length) {
        int end = offset + length, i = offset, mode = TEXT, subsetDepth = 0, hash = 1;
        int blockEnd = end - ((end - offset) & 7);
        while (i < blockEnd) {
            long word = littleEndian(source, i);
            long candidates = (word & HIGH_BITS) >>> 7;
            for (char c : CANDIDATES) candidates |= zeroByteMask(word ^ repeated(c));
            while (candidates != 0) {
                int position = i + (Long.numberOfTrailingZeros(candidates) >>> 3);
                // The byte-class table removes false-positive lanes from SWAR zero-byte detection.
                if ((CLASS[source[position] & 0xff] & 0xff) != 0) {
                    long next = step(source, position, mode, subsetDepth, hash, offset);
                    mode = (int) next & 0xff;
                    subsetDepth = (int) (next >>> 8) & 0xff;
                    hash = (int) (next >>> 16);
                }
                candidates &= candidates - 1;
            }
            i += 8;
        }
        for (; i < end; i++) {
            if ((CLASS[source[i] & 0xff] & 0xff) != 0) {
                long next = step(source, i, mode, subsetDepth, hash, offset);
                mode = (int) next & 0xff;
                subsetDepth = (int) (next >>> 8) & 0xff;
                hash = (int) (next >>> 16);
            }
        }
        return ((long) mode << 56) | ((long) subsetDepth << 48) | (hash & 0xffff_ffffL);
    }

    private static long step(byte[] s, int i, int mode, int depth, int hash, int base) {
        int c = s[i] & 0xff, event = 0;
        switch (mode) {
            case TEXT -> {
                if (c == '<') {
                    if (startsWith(s, i, "<!--")) { mode = COMMENT; event = 1; }
                    else if (startsWith(s, i, "<![CDATA[")) { mode = CDATA; event = 2; }
                    else if (startsWith(s, i, "<?")) { mode = PI; event = 3; }
                    else if (startsWith(s, i, "<!")) { mode = DECL; event = 4; }
                    else { mode = TAG; event = 5; }
                } else if (c == '&') { mode = ENTITY_TEXT; event = 6; }
            }
            case TAG -> {
                if (c == '\'') { mode = SINGLE_QUOTE; event = 7; }
                else if (c == '"') { mode = DOUBLE_QUOTE; event = 8; }
                else if (c == '>') { mode = TEXT; event = 9; }
            }
            case SINGLE_QUOTE -> {
                if (c == '\'') { mode = TAG; event = 10; }
                else if (c == '&') { mode = ENTITY_SINGLE_QUOTE; event = 6; }
            }
            case DOUBLE_QUOTE -> {
                if (c == '"') { mode = TAG; event = 11; }
                else if (c == '&') { mode = ENTITY_DOUBLE_QUOTE; event = 6; }
            }
            case ENTITY_TEXT -> { if (c == ';') { mode = TEXT; event = 12; } }
            case ENTITY_SINGLE_QUOTE -> { if (c == ';') { mode = SINGLE_QUOTE; event = 12; } }
            case ENTITY_DOUBLE_QUOTE -> { if (c == ';') { mode = DOUBLE_QUOTE; event = 12; } }
            case COMMENT -> { if (c == '-' && startsWith(s, i, "-->")) { mode = TEXT; event = 13; } }
            case CDATA -> { if (c == ']' && startsWith(s, i, "]]>")) { mode = TEXT; event = 14; } }
            case PI -> { if (c == '?' && startsWith(s, i, "?>")) { mode = TEXT; event = 15; } }
            case DECL -> {
                if (c == '\'') mode = DECL_SINGLE_QUOTE;
                else if (c == '"') mode = DECL_DOUBLE_QUOTE;
                else if (c == '[') depth++;
                else if (c == ']' && depth > 0) depth--;
                else if (c == '>' && depth == 0) { mode = TEXT; event = 16; }
            }
            case DECL_SINGLE_QUOTE -> { if (c == '\'') mode = DECL; }
            case DECL_DOUBLE_QUOTE -> { if (c == '"') mode = DECL; }
            default -> throw new IllegalStateException("Unknown XML scanner state " + mode);
        }
        if (event != 0) hash = 31 * hash + (i - base) * 17 + event;
        return ((long) hash << 16) | ((long) depth << 8) | mode;
    }

    private static boolean startsWith(byte[] source, int offset, String token) {
        if (offset + token.length() > source.length) return false;
        for (int i = 0; i < token.length(); i++) if (source[offset + i] != token.charAt(i)) return false;
        return true;
    }

    private static long zeroByteMask(long value) {
        return (value - LOW_BITS) & ~value & HIGH_BITS;
    }

    private static long repeated(char c) { return (c & 0xffL) * LOW_BITS; }

    private static long littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | (bytes[offset + 1] & 0xffL) << 8
                | (bytes[offset + 2] & 0xffL) << 16 | (bytes[offset + 3] & 0xffL) << 24
                | (bytes[offset + 4] & 0xffL) << 32 | (bytes[offset + 5] & 0xffL) << 40
                | (bytes[offset + 6] & 0xffL) << 48 | (bytes[offset + 7] & 0xffL) << 56;
    }

    private static byte[] classes() {
        byte[] classes = new byte[256];
        for (char c : CANDIDATES) classes[c] = (byte) (c == ' ' || c == '\t' || c == '\r' || c == '\n' ? 2 : 1);
        for (int i = 0x80; i < classes.length; i++) classes[i] = 3;
        return classes;
    }
}
