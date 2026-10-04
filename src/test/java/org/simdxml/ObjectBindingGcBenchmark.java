package org.simdxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
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
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.io.ByteArrayInputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

/**
 * JMH object-binding benchmark. Run with {@code -prof gc} to obtain
 * alloc.rate, alloc.count, gc.count and gc.time for each implementation.
 * Contexts, mappers and input bytes are prepared outside the measured methods.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2"})
@State(Scope.Benchmark)
public class ObjectBindingGcBenchmark {
    @Param({"simdxml", "simdxml-direct", "jaxb-ri", "jackson"})
    public String library;

    @Param({"unmarshal", "marshal"})
    public String operation;

    @Param({"32"})
    public int books;

    private byte[] xml;
    private Catalog source;
    private SimdUnmarshaller simdUnmarshaller;
    private SimdMarshaller simdMarshaller;
    private DirectSimdXmlParser directParser;
    private Arena directArena;
    private MemorySegment directInput;
    private Unmarshaller jaxbUnmarshaller;
    private Unmarshaller referenceUnmarshaller;
    private Marshaller jaxbMarshaller;
    private XmlMapper jackson;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        source = fixture(books);
        xml = SimdJaxbContext.builder(Catalog.class).build().createMarshaller().marshal(source);
        if ("simdxml".equals(library)) {
            // Keep the configured safety bound proportional to this fixture. The default JAXB
            // compatibility bound (1024) is intentionally generous, but would overstate the
            // allocation cost of a shallow message in this steady-state benchmark.
            SimdJaxbContext context = SimdJaxbContext.builder(Catalog.class)
                    .withCapacity(xml.length).withMaxDepth(64).build();
            simdUnmarshaller = context.createUnmarshaller();
            simdMarshaller = context.createMarshaller();
        } else if ("simdxml-direct".equals(library)) {
            if ("marshal".equals(operation))
                throw new UnsupportedOperationException("simdxml-direct benchmark currently covers unmarshal only");
            // The fixture is produced by the JAXB marshaller and is already valid UTF-8;
            // isolate binding/scanning throughput from the optional defensive validator.
            directParser = DirectSimdXmlParser.builder().withMaxDepth(64)
                    .withUtf8Validation(Utf8Validation.NONE).build();
            // Exercise the intended native/off-heap path. Heap segments cannot use the
            // Unsafe address fast path and would measure the checked FFM accessor instead.
            directArena = Arena.ofConfined();
            directInput = directArena.allocate(xml.length);
            directInput.copyFrom(MemorySegment.ofArray(xml));
        } else if ("jaxb-ri".equals(library)) {
            JAXBContext context = JAXBContext.newInstance(Catalog.class);
            jaxbUnmarshaller = context.createUnmarshaller();
            jaxbMarshaller = context.createMarshaller();
        } else if ("jackson".equals(library)) {
            jackson = new XmlMapper();
        } else {
            throw new IllegalArgumentException("Unknown library: " + library);
        }
        // Reference reader for the marshal check; independent of the backend under test.
        referenceUnmarshaller = JAXBContext.newInstance(Catalog.class).createUnmarshaller();
        if ("unmarshal".equals(operation)) verifyEquivalence();
        else verifyMarshalEquivalence();
    }

    /** A throughput comparison is meaningless unless every backend materializes the same graph. */
    private void verifyEquivalence() throws Exception {
        Catalog bound;
        if ("simdxml".equals(library)) bound = simdUnmarshaller.unmarshal(xml, Catalog.class);
        else if ("simdxml-direct".equals(library)) bound = directParser.bind(directInput, Catalog.class);
        else if ("jaxb-ri".equals(library)) bound = (Catalog) jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(xml));
        else bound = jackson.readValue(xml, Catalog.class);
        assertSameGraph(bound);
    }

    /**
     * The marshal counterpart of {@link #verifyEquivalence()}: whatever a backend writes is read
     * back with an independent JAXB RI reader and compared to the source graph. Without this, a
     * backend that emitted an empty or truncated document would be timed while doing no work —
     * exactly the failure that invalidated every Jackson figure recorded before 2026-08-22.
     */
    private void verifyMarshalEquivalence() throws Exception {
        byte[] produced;
        if ("simdxml".equals(library)) produced = simdMarshaller.marshal(source);
        else if ("jaxb-ri".equals(library)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(xml.length);
            jaxbMarshaller.marshal(source, output);
            produced = output.toByteArray();
        } else produced = jackson.writeValueAsBytes(source);
        if (produced == null || produced.length == 0)
            throw new IllegalStateException(library + " marshalled an empty document");
        assertSameGraph((Catalog) referenceUnmarshaller.unmarshal(new ByteArrayInputStream(produced)));
    }

    private void assertSameGraph(Catalog bound) {
        if (bound == null || bound.books == null || bound.books.size() != source.books.size())
            throw new IllegalStateException(library + " bound "
                    + (bound == null || bound.books == null ? "null" : bound.books.size())
                    + " books, expected " + source.books.size());
        for (int i = 0; i < source.books.size(); i++) {
            Book expected = source.books.get(i), actual = bound.books.get(i);
            if (expected.id != actual.id || !expected.title.equals(actual.title))
                throw new IllegalStateException(library + " differs at book " + i + ": "
                        + actual.id + "/" + actual.title + " != " + expected.id + "/" + expected.title);
        }
    }

    @Benchmark
    public void bind(Blackhole blackhole) throws Exception {
        if ("unmarshal".equals(operation)) {
            if ("simdxml".equals(library)) blackhole.consume(simdUnmarshaller.unmarshal(xml, Catalog.class));
            else if ("simdxml-direct".equals(library)) blackhole.consume(directParser.bind(directInput, Catalog.class));
            else if ("jaxb-ri".equals(library)) blackhole.consume(jaxbUnmarshaller.unmarshal(new ByteArrayInputStream(xml)));
            else blackhole.consume(jackson.readValue(xml, Catalog.class));
        } else {
            if ("simdxml-direct".equals(library))
                throw new UnsupportedOperationException("simdxml-direct benchmark currently covers unmarshal only");
            if ("simdxml".equals(library)) blackhole.consume(simdMarshaller.marshal(source));
            else if ("jaxb-ri".equals(library)) {
                // JAXB RI's byte[] allocation is intentionally part of the operation, like the others.
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(xml.length);
                jaxbMarshaller.marshal(source, output);
                blackhole.consume(output.toByteArray());
            } else blackhole.consume(jackson.writeValueAsBytes(source));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (directArena != null) directArena.close();
    }

    private static Catalog fixture(int count) {
        Catalog catalog = new Catalog();
        for (int i = 0; i < count; i++) {
            Book book = new Book();
            book.id = i;
            book.title = "XML book " + i + " — SIMDXML";
            catalog.books.add(book);
        }
        return catalog;
    }

    /*
     * The Jackson annotations are the databind-neutral half of the same mapping: without them
     * XmlMapper does not recognize JAXB annotations, silently binds zero books and would be
     * compared while doing almost no work. simdxml and JAXB RI ignore them.
     */
    @XmlRootElement(name = "catalog")
    @JacksonXmlRootElement(localName = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElement(name = "book")
        @JacksonXmlProperty(localName = "book")
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<Book> books = new ArrayList<Book>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute
        @JacksonXmlProperty(isAttribute = true, localName = "id")
        public int id;
        @XmlValue
        @JacksonXmlText
        public String title;
    }
}
