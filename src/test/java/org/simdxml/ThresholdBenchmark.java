package org.simdxml;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Does building the structural index pay below 4 KB on a real message shape?
 *
 * <p>The object-binding fixture already answered "no" at 1.407 bytes, but a catalog of short
 * elements is not the only shape that matters. These are the production fixtures the repository
 * ships: a SOAP 1.1 envelope (882 B), an IHE XCPD SOAP 1.2 message (1.809 B) and an ISO 20022
 * pain.001 (427 B) — attribute-heavy, namespace-heavy and deeply nested respectively.
 *
 * <p>{@code threshold} is the parser's tiny-document cutoff: {@code 4096} is the default, so the
 * index is never built; {@code 0} builds it always. Both run in the same JVM against the same
 * bytes, so the comparison needs no cross-fork reasoning.
 *
 * <p>{@code -Dorg.simdxml.utf8.vector.threshold} is read once into a static, so measuring it needs
 * one fork group per value; the setup line prints what each run actually resolved.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2"})
@State(Scope.Benchmark)
public class ThresholdBenchmark {
    @Param({"soap/standard-soap11.xml", "healthcare/ihe-xcpd-soap12.xml", "payments/pain.001.001.09.xml"})
    public String fixture;

    /** 4096 is the shipped default (index off below 4 KB); 0 forces the index on. */
    @Param({"4096", "0"})
    public int threshold;

    private byte[] xml;
    private SimdXmlParser parser;

    @Setup(Level.Trial)
    public void setup() {
        xml = resource(fixture);
        parser = SimdXmlParser.builder().withCapacity(Math.max(1024, xml.length))
                .withMaxDepth(64).withTinyDocumentThreshold(threshold).build();
        long events = count();
        if (events < 4) throw new IllegalStateException(fixture + " produced only " + events + " events");
        System.out.println("# " + fixture + " bytes=" + xml.length + " threshold=" + threshold
                + " indexing=" + parser.indexingStrategy() + " utf8=" + parser.utf8ValidationStrategy());
    }

    private long count() {
        SimdXmlStreamReader reader = parser.reusableStream(xml);
        long events = 0;
        while (reader.hasNext()) { if (reader.next() == XmlEvent.END_DOCUMENT) break; events++; }
        return events;
    }

    @Benchmark
    public void tokenize(Blackhole blackhole) {
        blackhole.consume(count());
    }

    private static byte[] resource(String name) {
        try (InputStream input = ThresholdBenchmark.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IllegalArgumentException("Missing fixture: " + name);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
