package org.simdxml;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Direct backends must agree byte for byte. The vector finder crosses its unrolled block, its
 * single-vector tail and its scalar tail at sizes that depend on the machine's species width, so
 * the sweep covers every length up to several blocks and every start offset inside a block.
 */
class DirectByteFinderEquivalenceTest {

    private static DirectByteFinder vector() {
        try {
            return (DirectByteFinder) Class.forName("org.simdxml.VectorDirectByteFinder")
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;   // No Vector API on this JVM: the SWAR pair below still runs.
        }
    }

    /** Definition of the answer, independent of both backends under test. */
    private static long reference(MemorySegment segment, long from, long to, byte target) {
        for (long i = from; i < to; i++)
            if (segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i) == target) return i;
        return to;
    }

    @Test
    void everyBackendAgreesWithTheReferenceOverOffsetsAndLengths() {
        DirectByteFinder scalar = new ScalarDirectByteFinder();
        DirectByteFinder vector = vector();
        Random random = new Random(20260827L);
        int maximum = 512;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(maximum);
            for (int trial = 0; trial < 64; trial++) {
                for (int i = 0; i < maximum; i++)
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, (byte) ('a' + random.nextInt(3)));
                // A sparse target so most blocks miss, plus dense trials where every block hits.
                byte target = (byte) '<';
                int hits = trial % 8 == 0 ? maximum / 4 : trial % 3;
                for (int h = 0; h < hits; h++)
                    segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, random.nextInt(maximum), target);

                for (int from = 0; from <= 40; from++) {
                    for (int length = 0; from + length <= maximum; length += (length < 80 ? 1 : 17)) {
                        final int start = from, span = length;
                        long to = start + span;
                        long expected = reference(segment, start, to, target);
                        assertEquals(expected, scalar.find(segment, start, to, target),
                                () -> scalarLabel(start, span));
                        if (vector != null)
                            assertEquals(expected, vector.find(segment, start, to, target),
                                    () -> vectorLabel(start, span));
                    }
                }
            }
        }
    }

    @Test
    void anAbsentTargetReturnsTheExclusiveEndOnEveryBackend() {
        DirectByteFinder scalar = new ScalarDirectByteFinder();
        DirectByteFinder vector = vector();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(300);
            for (int i = 0; i < 300; i++)
                segment.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, (byte) 'x');
            for (int from = 0; from < 17; from++) {
                for (int to = from; to <= 300; to++) {
                    assertEquals(to, scalar.find(segment, from, to, (byte) '<'));
                    if (vector != null) assertEquals(to, vector.find(segment, from, to, (byte) '<'));
                }
            }
        }
    }

    @Test
    void theSelectedBackendIsReportedAndUsable() {
        DirectSimdXmlParser parser = DirectSimdXmlParser.builder().build();
        String strategy = parser.memoryStrategy();
        assertTrue(strategy.contains("swar") || strategy.contains("vector"), strategy);
        DirectByteFinder vector = vector();
        if (vector != null) assertTrue(vector.name().startsWith("memorysegment-vector-"), vector.name());
    }

    private static String scalarLabel(int from, int length) { return "swar from=" + from + " length=" + length; }
    private static String vectorLabel(int from, int length) { return "vector from=" + from + " length=" + length; }
}
