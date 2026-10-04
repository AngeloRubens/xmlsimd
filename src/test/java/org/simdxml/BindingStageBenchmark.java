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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits XML → bean into the three stages the aggregate benchmark cannot tell apart.
 *
 * <ul>
 *   <li>{@code tokenize} — the reader alone: structural scan, name and text exposure, no objects.
 *   <li>{@code bindPretokenized} — the binder alone, replaying events recorded once in {@code @Setup}.
 *       Recorded events carry Strings, so this measures the binder's general path without the
 *       raw-byte fast path; it is a floor for binding cost, not a subtraction of {@code tokenize}.
 *   <li>{@code endToEnd} — the production path, for reference in the same run.
 * </ul>
 *
 * The three stages are separate {@code @Benchmark} methods so JMH reports each with its own error.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2"})
@State(Scope.Benchmark)
public class BindingStageBenchmark {
    @Param({"4", "32", "256"})
    public int books;

    private byte[] xml;
    private SimdXmlParser parser;
    private SimdUnmarshaller unmarshaller;
    private XmlBinder binder;
    private RecordedEvents recorded;

    @Setup(Level.Trial)
    public void setup() {
        Catalog source = fixture(books);
        SimdJaxbContext context = SimdJaxbContext.builder(Catalog.class).build();
        xml = context.createMarshaller().marshal(source);

        SimdJaxbContext sized = SimdJaxbContext.builder(Catalog.class)
                .withCapacity(xml.length).withMaxDepth(64).build();
        unmarshaller = sized.createUnmarshaller();
        parser = SimdXmlParser.builder().withCapacity(xml.length).withMaxDepth(64).build();
        binder = new XmlBinder(Collections.<Class<?>, XmlBindingAdapter>emptyMap());
        recorded = RecordedEvents.record(parser.reusableStream(xml));

        // Every stage must reach the same graph; a stage that silently binds nothing would
        // otherwise be timed while doing no work.
        verify(unmarshaller.unmarshal(xml, Catalog.class), source);
        verify(binder.bind(recorded.replay(), Catalog.class), source);
    }

    /** Stage 1: tokenization only. Names and text are touched so the scan cannot be folded away. */
    @Benchmark
    public void tokenize(Blackhole blackhole) {
        SimdXmlStreamReader reader = parser.reusableStream(xml);
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            blackhole.consume(event);
            // Attributes are already scanned by next(); materializing the map here would add
            // allocation that belongs to the binder, not to tokenization.
            if (event == XmlEvent.START_ELEMENT) {
                blackhole.consume(reader.localNameHash());
            } else if (event == XmlEvent.TEXT) {
                blackhole.consume(reader.rawTextBytes());
            }
            if (event == XmlEvent.END_DOCUMENT) break;
        }
    }

    /** Stage 2: the binder over events that were tokenized once, in {@code @Setup}. */
    @Benchmark
    public void bindPretokenized(Blackhole blackhole) {
        blackhole.consume(binder.bind(recorded.replay(), Catalog.class));
    }

    /** Stage 3: the production path, measured in the same run for reference. */
    @Benchmark
    public void endToEnd(Blackhole blackhole) {
        blackhole.consume(unmarshaller.unmarshal(xml, Catalog.class));
    }

    private static void verify(Catalog bound, Catalog source) {
        if (bound == null || bound.books.size() != source.books.size())
            throw new IllegalStateException("stage bound "
                    + (bound == null ? "null" : String.valueOf(bound.books.size()))
                    + " books, expected " + source.books.size());
        for (int i = 0; i < source.books.size(); i++)
            if (source.books.get(i).id != bound.books.get(i).id
                    || !source.books.get(i).title.equals(bound.books.get(i).title))
                throw new IllegalStateException("stage differs at book " + i);
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

    /**
     * One tokenization of the fixture, kept as plain events. {@link #replay()} hands the binder a
     * cursor that does no scanning at all, so what is left is binder work: dispatch, conversion,
     * bean construction and list growth.
     */
    static final class RecordedEvents {
        private final XmlEvent[] events;
        private final String[] names;
        private final String[] texts;
        private final Map<String, String>[] attributes;

        private RecordedEvents(XmlEvent[] events, String[] names, String[] texts, Map<String, String>[] attributes) {
            this.events = events; this.names = names; this.texts = texts; this.attributes = attributes;
        }

        @SuppressWarnings("unchecked")
        static RecordedEvents record(SimdXmlStreamReader reader) {
            List<XmlEvent> events = new ArrayList<XmlEvent>();
            List<String> names = new ArrayList<String>();
            List<String> texts = new ArrayList<String>();
            List<Map<String, String>> attributes = new ArrayList<Map<String, String>>();
            while (reader.hasNext()) {
                XmlEvent event = reader.next();
                events.add(event);
                names.add(event == XmlEvent.START_ELEMENT || event == XmlEvent.END_ELEMENT ? reader.name() : null);
                texts.add(event == XmlEvent.TEXT || event == XmlEvent.CDATA ? reader.text() : null);
                attributes.add(event == XmlEvent.START_ELEMENT
                        ? new LinkedHashMap<String, String>(reader.attributes())
                        : Collections.<String, String>emptyMap());
                if (event == XmlEvent.END_DOCUMENT) break;
            }
            return new RecordedEvents(events.toArray(new XmlEvent[0]), names.toArray(new String[0]),
                    texts.toArray(new String[0]), attributes.toArray(new Map[0]));
        }

        BindingXmlReader replay() { return new Replay(); }

        private final class Replay implements BindingXmlReader {
            private int index = -1;
            public XmlEvent next() { return events[++index]; }
            public boolean hasNext() { return index + 1 < events.length; }
            public String name() { return names[index]; }
            public String text() { return texts[index]; }
            public Map<String, String> attributes() { return attributes[index]; }
            public String attribute(String key) { return attributes[index].get(key); }
            public boolean hasNamespaceDeclarations() {
                for (String key : attributes[index].keySet())
                    if (key.equals("xmlns") || key.startsWith("xmlns:")) return true;
                return false;
            }
        }
    }

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
}
