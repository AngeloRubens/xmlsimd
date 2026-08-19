package org.simdxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** A/B benchmark for healthcare projection: vertical stream, generic stream, and JAXB RI binding. */
public final class HealthcareVerticalBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: HealthcareVerticalBenchmark <vertical-on|vertical-off|direct-vertical|jaxb> <soap-file> [iterations]");
        String mode = args[0]; byte[] xml = Files.readAllBytes(Path.of(args[1]));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 10_000;
        Body body = switch (mode) {
            case "vertical-on" -> simd(xml.length, true);
            case "vertical-off" -> simd(xml.length, false);
            case "direct-vertical" -> direct(xml);
            case "jaxb" -> jaxb();
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
        long checksum = 0;
        for (int i = 0; i < Math.min(2_000, iterations); i++) checksum = Long.rotateLeft(checksum, 5) ^ body.consume(xml);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 5) ^ body.consume(xml);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT, "mode=%s messages/s=%.2f ns/message=%.1f checksum=%d%n",
                mode, iterations / seconds, seconds * 1e9 / iterations, checksum);
    }

    private static Body simd(int capacity, boolean vertical) {
        SimdXmlParser parser = new SimdXmlParser(capacity, 128, vertical);
        return xml -> checksum(parser.inspectHealthcare(xml));
    }
    private static Body direct(byte[] xml) {
        DirectSimdXmlParser parser = new DirectSimdXmlParser(128, Utf8Validation.STRICT);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(xml.length).put(xml).flip();
        return ignored -> checksum(parser.inspectHealthcare(buffer.duplicate()));
    }
    private static Body jaxb() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Envelope.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        return xml -> {
            Envelope envelope = (Envelope) unmarshaller.unmarshal(new ByteArrayInputStream(xml));
            String payload = envelope.body == null || envelope.body.payload == null
                    ? null : envelope.body.payload.getTagName();
            return mix(envelope.header == null ? null : envelope.header.action,
                    envelope.header == null ? null : envelope.header.messageId, payload);
        };
    }
    private static long checksum(HealthcareMessageInfo info) {
        return mix(info.action(), info.messageId(), info.payloadName());
    }
    private static long mix(String... values) {
        long hash = 1; for (String value : values) hash = hash * 31 + (value == null ? 0 : value.hashCode()); return hash;
    }
    private interface Body { long consume(byte[] xml) throws Exception; }

    @XmlRootElement(name = "Envelope", namespace = "http://www.w3.org/2003/05/soap-envelope")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Envelope {
        @XmlElement(name = "Header", namespace = "http://www.w3.org/2003/05/soap-envelope") public Header header;
        @XmlElement(name = "Body", namespace = "http://www.w3.org/2003/05/soap-envelope") public BodyElement body;
    }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Header {
        @XmlElement(name = "Action", namespace = "http://www.w3.org/2005/08/addressing") public String action;
        @XmlElement(name = "MessageID", namespace = "http://www.w3.org/2005/08/addressing") public String messageId;
    }
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class BodyElement { @XmlAnyElement public Element payload; }
    private HealthcareVerticalBenchmark() { }
}
