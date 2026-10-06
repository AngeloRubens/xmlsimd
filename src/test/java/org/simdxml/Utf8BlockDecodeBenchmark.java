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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Compares JDK UTF-8 decoding with an ASCII-block fast path suitable for parser input readers. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class Utf8BlockDecodeBenchmark {
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final byte[] WIDTH = widths();
    private static final byte[] SECOND_MIN = secondMinimums();
    private static final byte[] SECOND_MAX = secondMaximums();

    @Param({"4096", "65536"}) public int targetBytes;
    @Param({"ascii", "mixed", "unicode"}) public String content;

    private byte[] input;
    private char[] output;
    private ByteBuffer decoderInput;
    private CharBuffer decoderOutput;
    private CharsetDecoder decoder;

    @Setup(Level.Trial)
    public void setup() {
        String unit = switch (content) {
            case "ascii" -> "<entry id=\"184\"><name>XML scanner</name><count>12345</count></entry>";
            case "mixed" -> "<entry id=\"184\"><name>XML café 東京</name><count>12345</count></entry>";
            case "unicode" -> "<entry id=\"184\"><name>café 東京 🧪</name><count>12345</count></entry>";
            default -> throw new IllegalArgumentException(content);
        };
        StringBuilder document = new StringBuilder(targetBytes + 64).append("<feed>");
        while (document.length() < targetBytes) document.append(unit);
        document.append("</feed>");
        input = document.toString().getBytes(StandardCharsets.UTF_8);
        output = new char[input.length];
        decoderInput = ByteBuffer.wrap(input);
        decoderOutput = CharBuffer.allocate(input.length);
        decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String expected = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(input)).toString();
            char[] actual = new char[input.length];
            int length = decode(input, input.length, actual);
            if (!expected.equals(new String(actual, 0, length))) {
                throw new IllegalStateException("UTF-8 block decoder produced different characters");
            }
            if (hash(expected) != decodeAndHash(input, input.length, new char[input.length])) {
                throw new IllegalStateException("UTF-8 block decoder produced a different checksum");
            }
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("Invalid benchmark fixture", e);
        }
    }

    @Benchmark
    public int jdkStringDecode() {
        String decoded = new String(input, StandardCharsets.UTF_8);
        int hash = 1;
        for (int i = 0; i < decoded.length(); i++) hash = 31 * hash + decoded.charAt(i);
        return hash;
    }

    @Benchmark
    public int jdkDecoderReusable() throws CharacterCodingException {
        decoder.reset();
        decoderInput.position(0);
        decoderOutput.clear();
        decoder.decode(decoderInput, decoderOutput, true);
        decoder.flush(decoderOutput);
        decoderOutput.flip();
        int hash = 1;
        while (decoderOutput.hasRemaining()) hash = 31 * hash + decoderOutput.get();
        return hash;
    }

    @Benchmark
    public int swarBlocksReusable() {
        return decodeAndHash(input, input.length, output);
    }

    @Benchmark
    public int swarBlocksAllocated() {
        return decodeAndHash(input, input.length, new char[input.length]);
    }

    static int decode(byte[] source, int length, char[] destination) throws MalformedInputException {
        return decode(source, length, destination, false);
    }

    private static int decodeAndHash(byte[] source, int length, char[] destination) {
        try {
            return decode(source, length, destination, true);
        } catch (MalformedInputException e) {
            throw new IllegalArgumentException("Malformed UTF-8 at benchmark input", e);
        }
    }

    private static int hash(String value) {
        int hash = 1;
        for (int i = 0; i < value.length(); i++) hash = 31 * hash + value.charAt(i);
        return hash;
    }

    private static int decode(byte[] source, int length, char[] destination, boolean hashOutput)
            throws MalformedInputException {
        int in = 0, out = 0, hash = 1;
        int blockBound = length & ~31;
        while (in < blockBound) {
            long high = littleEndian(source, in) | littleEndian(source, in + 8)
                    | littleEndian(source, in + 16) | littleEndian(source, in + 24);
            if ((high & HIGH_BITS) != 0) break;
            for (int end = in + 32; in < end; in++) {
                char value = (char) source[in];
                destination[out++] = value;
                if (hashOutput) hash = 31 * hash + value;
            }
        }
        while (in < length) {
            int first = source[in] & 0xff;
            int width = WIDTH[first] & 0xff;
            if (width == 0 || in + width > length) throw new MalformedInputException(Math.max(1, length - in));
            int codePoint;
            if (width == 1) {
                codePoint = first;
            } else {
                int second = source[in + 1] & 0xff;
                if (second < (SECOND_MIN[first] & 0xff) || second > (SECOND_MAX[first] & 0xff)) {
                    throw new MalformedInputException(width);
                }
                codePoint = first & (width == 2 ? 0x1f : width == 3 ? 0x0f : 0x07);
                codePoint = (codePoint << 6) | (second & 0x3f);
                for (int continuation = 2; continuation < width; continuation++) {
                    int next = source[in + continuation] & 0xff;
                    if ((next & 0xc0) != 0x80) throw new MalformedInputException(width);
                    codePoint = (codePoint << 6) | (next & 0x3f);
                }
            }
            in += width;
            if (codePoint <= 0xffff) {
                char value = (char) codePoint;
                destination[out++] = value;
                if (hashOutput) hash = 31 * hash + value;
            } else {
                int supplementary = codePoint - 0x10000;
                char highSurrogate = (char) (0xd800 | (supplementary >>> 10));
                char lowSurrogate = (char) (0xdc00 | (supplementary & 0x3ff));
                destination[out++] = highSurrogate;
                destination[out++] = lowSurrogate;
                if (hashOutput) hash = 31 * (31 * hash + highSurrogate) + lowSurrogate;
            }
        }
        return hashOutput ? hash : out;
    }

    private static long littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | (bytes[offset + 1] & 0xffL) << 8
                | (bytes[offset + 2] & 0xffL) << 16 | (bytes[offset + 3] & 0xffL) << 24
                | (bytes[offset + 4] & 0xffL) << 32 | (bytes[offset + 5] & 0xffL) << 40
                | (bytes[offset + 6] & 0xffL) << 48 | (bytes[offset + 7] & 0xffL) << 56;
    }

    private static byte[] widths() {
        byte[] table = new byte[256];
        java.util.Arrays.fill(table, 0, 0x80, (byte) 1);
        java.util.Arrays.fill(table, 0xc2, 0xe0, (byte) 2);
        java.util.Arrays.fill(table, 0xe0, 0xf0, (byte) 3);
        java.util.Arrays.fill(table, 0xf0, 0xf5, (byte) 4);
        return table;
    }

    private static byte[] secondMinimums() {
        byte[] table = new byte[256];
        java.util.Arrays.fill(table, (byte) 0x80);
        table[0xe0] = (byte) 0xa0;
        table[0xf0] = (byte) 0x90;
        return table;
    }

    private static byte[] secondMaximums() {
        byte[] table = new byte[256];
        java.util.Arrays.fill(table, (byte) 0xbf);
        table[0xed] = (byte) 0x9f;
        table[0xf4] = (byte) 0x8f;
        return table;
    }
}
