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

/** Compares scalar and SWAR scans for XML markup and entity delimiters in UTF-8 input. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class XmlStructuralScanBenchmark {
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final long LOW_BITS = 0x0101010101010101L;
    private static final char[] TOKENS = "<>&;\"'/?=![]- \t\r\n".toCharArray();
    private static final byte[] CLASS = classes();

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
        StringBuilder document = new StringBuilder(targetBytes + 64).append("<feed>");
        while (document.length() < targetBytes) document.append(unit);
        document.append("</feed>");
        input = document.toString().getBytes(StandardCharsets.UTF_8);
        long expected = scanScalar(input, 0, input.length);
        long actual = scanSwar(input, 0, input.length);
        if (expected != actual) {
            throw new IllegalStateException("SWAR scanner disagrees with scalar scanner");
        }
    }

    @Benchmark
    public long scalar() {
        return scanScalar(input, 0, input.length);
    }

    @Benchmark
    public long swar() {
        return scanSwar(input, 0, input.length);
    }

    static long scanScalar(byte[] source, int offset, int length) {
        int end = offset + length;
        long count = 0, checksum = 1;
        for (int i = offset; i < end; i++) {
            int type = CLASS[source[i] & 0xff] & 0xff;
            if (type != 0) {
                count++;
                checksum = 31 * checksum + (i - offset) * 17L + type;
            }
        }
        return (count << 32) | (checksum & 0xffff_ffffL);
    }

    static long scanSwar(byte[] source, int offset, int length) {
        int end = offset + length;
        int i = offset;
        long count = 0, checksum = 1;
        int blockEnd = end - ((end - offset) & 7);
        while (i < blockEnd) {
            long word = littleEndian(source, i);
            long candidates = (word & HIGH_BITS) >>> 7;
            for (char token : TOKENS) {
                candidates |= zeroByteMask(word ^ repeated(token));
            }
            while (candidates != 0) {
                int lane = Long.numberOfTrailingZeros(candidates) >>> 3;
                int position = i + lane;
                int type = CLASS[source[position] & 0xff] & 0xff;
                if (type != 0) {
                    count++;
                    checksum = 31 * checksum + (position - offset) * 17L + type;
                }
                candidates &= candidates - 1;
            }
            i += 8;
        }
        for (; i < end; i++) {
            int type = CLASS[source[i] & 0xff] & 0xff;
            if (type != 0) {
                count++;
                checksum = 31 * checksum + (i - offset) * 17L + type;
            }
        }
        return (count << 32) | (checksum & 0xffff_ffffL);
    }

    private static long zeroByteMask(long value) {
        // The mask may include false positives after byte-lane borrows; candidate bytes are verified.
        return (value - LOW_BITS) & ~value & HIGH_BITS;
    }

    private static byte[] classes() {
        byte[] classes = new byte[256];
        for (char token : TOKENS) classes[token] = (byte) (token == ' ' || token == '\t'
                || token == '\r' || token == '\n' ? 2 : 1);
        for (int i = 0x80; i < classes.length; i++) classes[i] = 4;
        return classes;
    }

    private static long repeated(char value) {
        return (value & 0xffL) * LOW_BITS;
    }

    private static long littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | (bytes[offset + 1] & 0xffL) << 8
                | (bytes[offset + 2] & 0xffL) << 16 | (bytes[offset + 3] & 0xffL) << 24
                | (bytes[offset + 4] & 0xffL) << 32 | (bytes[offset + 5] & 0xffL) << 40
                | (bytes[offset + 6] & 0xffL) << 48 | (bytes[offset + 7] & 0xffL) << 56;
    }
}
