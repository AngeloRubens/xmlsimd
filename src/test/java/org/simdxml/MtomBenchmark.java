package org.simdxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlInlineBinaryData;
import jakarta.xml.bind.annotation.XmlMimeType;
import jakarta.xml.bind.annotation.XmlRootElement;
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
import java.util.HashMap;
import java.util.Map;

/**
 * MTOM cost, measured against the framework-neutral attachment bridge rather than any JAXB provider.
 *
 * <p>{@code mode} separates the three shapes the marshaller takes:
 * <ul>
 *   <li>{@code inline} — {@code @XmlInlineBinaryData}: base64 in the document, no attachment;
 *   <li>{@code mtom-bytes} — {@code byte[]} handed to the provider by offset and length, no copy;
 *   <li>{@code mtom-object} — a value the provider takes directly, standing in for a DataHandler,
 *       so the core never materializes a {@code byte[]};
 *   <li>{@code unmarshal} — reading a document whose element is a {@code <xop:Include/>}.
 * </ul>
 * The attachment store is a fixed map, so what is measured is the binder and the writer, not I/O.
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-XX:+UseG1GC", "-XX:ActiveProcessorCount=2"})
@State(Scope.Benchmark)
public class MtomBenchmark {
    @Param({"inline", "mtom-bytes", "mtom-object", "unmarshal"})
    public String mode;

    @Param({"1024", "65536"})
    public int payload;

    private SimdMarshaller marshaller;
    private SimdUnmarshaller unmarshaller;
    private Object source;
    private byte[] xopDocument;
    private Class<?> type;


    @Setup(Level.Trial)
    public void setup() {
        byte[] data = new byte[payload];
        for (int i = 0; i < payload; i++) data[i] = (byte) i;
        final Map<String, byte[]> parts = new HashMap<String, byte[]>();
        parts.put("one", data);

        XmlAttachmentHandler handler = new XmlAttachmentHandler() {
            public boolean isXopPackage() { return true; }
            public String addMtomAttachment(byte[] d, int o, int l, String t, String ns, String local) { return "cid:one"; }
            public String addMtomAttachment(Object value, String ns, String local) {
                // The provider takes the DataHandler as-is: the core never materializes a byte[].
                return value instanceof jakarta.activation.DataHandler ? "cid:one" : null;
            }
            public byte[] getAttachmentAsByteArray(String cid) { return parts.get(cid); }
            public byte[] toBytes(Object value) { return data; }
        };

        if ("inline".equals(mode)) {
            type = InlineDocument.class;
            InlineDocument document = new InlineDocument(); document.payload = data; source = document;
        } else if ("mtom-object".equals(mode)) {
            type = BlobDocument.class;
            BlobDocument document = new BlobDocument(); document.payload = handlerFor(data); source = document;
        } else {
            type = BinaryDocument.class;
            BinaryDocument document = new BinaryDocument(); document.payload = data; source = document;
        }

        SimdJaxbContext context = SimdJaxbContext.builder(type).build();
        marshaller = context.createMarshaller().withAttachmentHandler(handler);
        unmarshaller = context.createUnmarshaller().withAttachmentHandler(handler);

        xopDocument = ("<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:one\"/></payload><name>n</name></binary>")
                .getBytes(StandardCharsets.UTF_8);

        verify();
    }

    private void verify() {
        if ("unmarshal".equals(mode)) {
            BinaryDocument bound = (BinaryDocument) unmarshaller.unmarshal(xopDocument, BinaryDocument.class);
            if (bound.payload == null || bound.payload.length != payload)
                throw new IllegalStateException("unmarshal bound " + (bound.payload == null ? "null" : bound.payload.length));
            return;
        }
        byte[] produced = marshaller.marshal(source);
        String text = new String(produced, StandardCharsets.UTF_8);
        boolean xop = text.contains("xop:Include");
        if ("inline".equals(mode) == xop)
            throw new IllegalStateException(mode + " produced the wrong shape: " + text.substring(0, Math.min(120, text.length())));
    }

    @Benchmark
    public void run(Blackhole blackhole) {
        if ("unmarshal".equals(mode)) blackhole.consume(unmarshaller.unmarshal(xopDocument, BinaryDocument.class));
        else blackhole.consume(marshaller.marshal(source));
    }

    @XmlRootElement(name = "binary")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BinaryDocument {
        @XmlElement @XmlMimeType("application/octet-stream") public byte[] payload;
        @XmlElement public String name;
        public BinaryDocument() { }
    }

    @XmlRootElement(name = "inline")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class InlineDocument {
        @XmlElement @XmlInlineBinaryData public byte[] payload;
        public InlineDocument() { }
    }

    @XmlRootElement(name = "blob")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BlobDocument {
        @XmlElement public jakarta.activation.DataHandler payload;
        public BlobDocument() { }
    }

    private static jakarta.activation.DataHandler handlerFor(final byte[] data) {
        return new jakarta.activation.DataHandler(new jakarta.activation.DataSource() {
            public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(data); }
            public java.io.OutputStream getOutputStream() { throw new UnsupportedOperationException(); }
            public String getContentType() { return "application/octet-stream"; }
            public String getName() { return "part"; }
        });
    }
}
