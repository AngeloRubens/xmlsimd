package org.simdxml;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.xml.XmlFactory;
import com.ctc.wstx.stax.WstxInputFactory;
import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Cross-library pull-parser benchmark. Each body consumes the complete input without building a DOM. */
public final class XmlLibraryBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: XmlLibraryBenchmark <simdxml|simdxml-reuse|simdxml-bytes|simdxml-direct|jdk-stax|woodstox|jackson-woodstox|jackson-jdk|xerces-sax> <xml-file> [iterations]");
        String library = args[0];
        byte[] xml = Files.readAllBytes(Path.of(args[1]));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        Body body = switch (library) {
            case "simdxml" -> new SimdBody(xml.length);
            case "simdxml-reuse" -> new SimdBody(xml.length, true);
            case "simdxml-bytes" -> new SimdByteBody(xml.length);
            case "simdxml-direct" -> new SimdDirectBody();
            case "jdk-stax" -> new StaxBody(XMLInputFactory.newDefaultFactory());
            case "woodstox" -> new StaxBody(new WstxInputFactory());
            case "jackson-woodstox" -> new JacksonBody(new WstxInputFactory());
            case "jackson-jdk" -> new JacksonBody(XMLInputFactory.newDefaultFactory());
            case "xerces-sax" -> new XercesSaxBody();
            default -> throw new IllegalArgumentException("Unknown library: " + library);
        };
        long checksum = 0;
        for (int i = 0; i < 2; i++) checksum = Long.rotateLeft(checksum, 7) ^ body.consume(xml);
        long total = 0, best = Long.MAX_VALUE;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            checksum = Long.rotateLeft(checksum, 7) ^ body.consume(xml);
            long elapsed = System.nanoTime() - start;
            total += elapsed; best = Math.min(best, elapsed);
        }
        double mib = xml.length / 1048576.0;
        double averageSeconds = total / 1e9 / iterations;
        System.out.printf(Locale.ROOT, "library=%s implementation=%s size=%.4f MiB avg=%.2f MiB/s best=%.2f MiB/s documents/s=%.2f ns/document=%.1f checksum=%d%n",
                library, body.implementation(), mib, mib / averageSeconds, mib / (best / 1e9),
                1.0 / averageSeconds, averageSeconds * 1e9, checksum);
    }

    private static final class SimdByteBody implements Body {
        private final SimdXmlParser parser;
        private SimdByteBody(int capacity) { parser = new SimdXmlParser(capacity, 4096); }
        @Override public String implementation() { return "simdxml-sbe-flyweight[" + parser.optimizationProfile(true) + "]"; }
        @Override public long consume(byte[] xml) {
            SimdXmlStreamReader reader = parser.reusableStream(xml); long hash = 0;
            while (reader.hasNext()) {
                XmlEvent event = reader.next(); hash = hash * 31 + event.ordinal();
                if (event == XmlEvent.START_ELEMENT || event == XmlEvent.END_ELEMENT) hash += reader.nameBytes().length();
                else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) hash += reader.rawTextBytes().length();
            }
            return hash;
        }
    }

    private static final class SimdDirectBody implements Body, DirectXmlEventConsumer {
        private final Utf8Validation validation = System.getProperty("org.simdxml.utf8.validation", "strict").equalsIgnoreCase("none")
                ? Utf8Validation.NONE : Utf8Validation.STRICT;
        private final DirectSimdXmlParser parser = new DirectSimdXmlParser(4096, validation);
        private java.nio.ByteBuffer direct;
        private long hash;
        @Override public String implementation() { return "simdxml-memorysegment-direct[utf8=" + validation + ",flyweight-slices,finder=" + parser.memoryStrategy() + "]"; }
        @Override public long consume(byte[] xml) {
            if (direct == null) {
                direct = java.nio.ByteBuffer.allocateDirect(xml.length);
                direct.put(xml).flip(); // one-time fixture/network-buffer setup, outside measured steady-state parsing
            }
            hash = 0;
            parser.scan(direct.duplicate(), this);
            return hash;
        }
        @Override public void onEvent(XmlEvent event, DirectXmlByteSlice name, DirectXmlByteSlice text) {
            hash = hash * 31 + event.ordinal();
            if (name != null) hash += name.length();
            else if (text != null && (event == XmlEvent.TEXT || event == XmlEvent.CDATA)) hash += text.length();
        }
    }

    private interface Body {
        long consume(byte[] xml) throws Exception;
        default String implementation() { return getClass().getSimpleName(); }
    }

    private static final class SimdBody implements Body {
        private final SimdXmlParser parser;
        private final boolean reuse;
        private SimdBody(int capacity) { this(capacity, false); }
        private SimdBody(int capacity, boolean reuse) { parser = new SimdXmlParser(capacity, 4096); this.reuse = reuse; }
        @Override public String implementation() {
            return "simdxml[indexer=" + parser.indexingStrategy() + ",utf8=" + parser.utf8Validation()
                    + ",utf8Strategy=" + parser.utf8ValidationStrategy() + ","
                    + parser.optimizationProfile(reuse) + "]";
        }
        @Override public long consume(byte[] xml) {
            SimdXmlStreamReader reader = reuse ? parser.reusableStream(xml) : parser.stream(xml); long hash = 0;
            while (reader.hasNext()) {
                XmlEvent event = reader.next(); hash = hash * 31 + event.ordinal();
                if (event == XmlEvent.START_ELEMENT || event == XmlEvent.END_ELEMENT) hash += reader.name().length();
                else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) hash += reader.text().length();
            }
            return hash;
        }
    }

    private static final class StaxBody implements Body {
        private final XMLInputFactory factory;
        private StaxBody(XMLInputFactory factory) {
            this.factory = factory;
            set(XMLInputFactory.SUPPORT_DTD, false);
            set("javax.xml.stream.isSupportingExternalEntities", false);
            set(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        }
        private void set(String property, Object value) {
            try { factory.setProperty(property, value); } catch (IllegalArgumentException ignored) { }
        }
        @Override public String implementation() {
            Package type = factory.getClass().getPackage();
            String version = type.getImplementationVersion();
            return factory.getClass().getName() + "@" + (version == null ? "JDK" : version);
        }
        @Override public long consume(byte[] xml) throws Exception {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml)); long hash = 0;
            try {
                while (reader.hasNext()) {
                    int event = reader.next(); hash = hash * 31 + event;
                    if (event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.END_ELEMENT)
                        hash += reader.getName().toString().length();
                    else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        hash += reader.getTextLength();
                }
            } finally { reader.close(); }
            return hash;
        }
    }

    private static final class JacksonBody implements Body {
        private final XmlFactory factory;
        private JacksonBody(XMLInputFactory inputFactory) { factory = new XmlFactory(inputFactory); }
        @Override public String implementation() {
            return "Jackson-" + factory.version() + "+" + factory.getXMLInputFactory().getClass().getName();
        }
        @Override public long consume(byte[] xml) throws Exception {
            long hash = 0;
            try (JsonParser parser = factory.createParser(xml)) {
                JsonToken token;
                while ((token = parser.nextToken()) != null) {
                    hash = hash * 31 + token.ordinal();
                    if (token == JsonToken.PROPERTY_NAME) hash += parser.currentName().length();
                    else if (token.isScalarValue()) hash += parser.getTextLength();
                }
            }
            return hash;
        }
    }

    private static final class XercesSaxBody implements Body {
        private final SAXParser parser = new SAXParser();
        private final HashingHandler handler = new HashingHandler();
        private XercesSaxBody() throws Exception {
            parser.setFeature("http://xml.org/sax/features/validation", false);
            parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
            parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            parser.setContentHandler(handler);
        }
        @Override public String implementation() {
            return parser.getClass().getName() + "@" + parser.getClass().getPackage().getImplementationVersion();
        }
        @Override public long consume(byte[] xml) throws Exception {
            handler.hash = 0;
            parser.parse(new InputSource(new ByteArrayInputStream(xml)));
            return handler.hash;
        }
        private static final class HashingHandler extends DefaultHandler {
            long hash;
            @Override public void startElement(String uri, String localName, String qName, Attributes attributes) {
                hash = hash * 31 + 1 + qName.length();
            }
            @Override public void endElement(String uri, String localName, String qName) {
                hash = hash * 31 + 2 + qName.length();
            }
            @Override public void characters(char[] ch, int start, int length) { hash = hash * 31 + 3 + length; }
        }
    }

    private XmlLibraryBenchmark() { }
}
