package org.simdxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * How the input is held changes which accessor the Direct parser can use: a heap
 * {@code MemorySegment} cannot take the unsafe address path a native one takes, and a direct
 * {@code ByteBuffer} is wrapped through {@code MemorySegment.ofBuffer}. This measures the three
 * against the same document and the same binder, and prints the strategy each run resolved.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2",
        "--enable-native-access=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class DirectMemoryBenchmark {
    @Param({"heap-segment", "native-segment", "direct-buffer"})
    public String memory;

    @Param({"4", "32", "256"})
    public int books;

    /**
     * {@code text} is the catalog fixture the aggregate benchmark uses, whose element content is a
     * String. {@code numeric} puts int, long, double and boolean in element text instead, which is
     * the shape the binder's retained-slice conversion exists for: it is the only case where the
     * String the old path built was pure waste.
     */
    @Param({"text", "numeric"})
    public String content;

    private DirectSimdXmlParser parser;
    private Arena arena;
    private MemorySegment segment;
    private ByteBuffer buffer;
    private Class<?> type;

    @Setup(Level.Trial)
    public void setup() {
        StringBuilder text = new StringBuilder("<catalog>");
        if ("numeric".equals(content))
            for (int i = 0; i < books; i++)
                text.append("<row><id>").append(i).append("</id><size>").append(i).append("00</size><score>")
                    .append(i).append(".25</score><active>true</active></row>");
        else
            for (int i = 0; i < books; i++)
                text.append("<book id='").append(i).append("'>XML book ").append(i).append(" SIMDXML</book>");
        text.append("</catalog>");
        byte[] xml = text.toString().getBytes(StandardCharsets.UTF_8);
        type = "numeric".equals(content) ? NumericCatalog.class : Catalog.class;

        // The fixture is ASCII and already valid: isolate scanning from the defensive validator.
        parser = DirectSimdXmlParser.builder().withMaxDepth(32)
                .withUtf8Validation(Utf8Validation.NONE).build();

        if ("heap-segment".equals(memory)) {
            segment = MemorySegment.ofArray(xml);
        } else if ("native-segment".equals(memory)) {
            arena = Arena.ofConfined();
            segment = arena.allocate(xml.length);
            segment.copyFrom(MemorySegment.ofArray(xml));
        } else if ("direct-buffer".equals(memory)) {
            buffer = ByteBuffer.allocateDirect(xml.length);
            buffer.put(xml).flip();
        } else {
            throw new IllegalArgumentException("Unknown memory kind: " + memory);
        }

        int size = boundSize(bindOnce());
        if (size != books)
            throw new IllegalStateException("bound " + size + " rows, expected " + books);
        System.out.println("# direct memory: " + memory + "/" + content
                + " strategy=" + parser.memoryStrategy());
    }

    private Object bindOnce() {
        return buffer != null ? parser.bind(buffer.duplicate(), type) : parser.bind(segment, type);
    }

    private static int boundSize(Object bound) {
        return bound instanceof Catalog ? ((Catalog) bound).books.size() : ((NumericCatalog) bound).rows.size();
    }

    @Benchmark
    public void bind(Blackhole blackhole) {
        blackhole.consume(bindOnce());
    }

    @TearDown(Level.Trial)
    public void tearDown() { if (arena != null) arena.close(); }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElement(name = "book") public List<Book> books = new ArrayList<Book>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute public int id;
        @XmlValue public String title;
    }

    /** Element text that is a number or a boolean: no String is needed, only the value. */
    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NumericCatalog {
        @XmlElement(name = "row") public List<NumericRow> rows = new ArrayList<NumericRow>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NumericRow {
        @XmlElement public int id;
        @XmlElement public long size;
        @XmlElement public double score;
        @XmlElement public boolean active;
    }
}
