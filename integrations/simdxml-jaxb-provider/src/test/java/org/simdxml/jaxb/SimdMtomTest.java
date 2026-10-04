package org.simdxml.jaxb;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAttachmentRef;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlInlineBinaryData;
import jakarta.xml.bind.annotation.XmlMimeType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.attachment.AttachmentMarshaller;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** MTOM/XOP and swaRef binding through the standard JAXB attachment callbacks only. */
final class SimdMtomTest {

    /** Standard-API attachment store: both callbacks over one content-id keyed map. */
    static final class Store {
        final Map<String, byte[]> parts = new HashMap<>();
        final Map<String, String> types = new HashMap<>();
        boolean xop = true;
        int next;

        AttachmentMarshaller marshaller() {
            return new AttachmentMarshaller() {
                @Override public String addMtomAttachment(DataHandler data, String ns, String local) {
                    try (InputStream in = data.getInputStream()) { return put(in.readAllBytes(), data.getContentType()); }
                    catch (IOException e) { throw new IllegalStateException(e); }
                }
                @Override public String addMtomAttachment(byte[] data, int offset, int length, String type, String ns, String local) {
                    byte[] copy = new byte[length];
                    System.arraycopy(data, offset, copy, 0, length);
                    return put(copy, type);
                }
                @Override public String addSwaRefAttachment(DataHandler data) {
                    try (InputStream in = data.getInputStream()) { return put(in.readAllBytes(), data.getContentType()); }
                    catch (IOException e) { throw new IllegalStateException(e); }
                }
                @Override public boolean isXOPPackage() { return xop; }
            };
        }

        AttachmentUnmarshaller unmarshaller() {
            return new AttachmentUnmarshaller() {
                @Override public byte[] getAttachmentAsByteArray(String cid) { return parts.get(strip(cid)); }
                @Override public DataHandler getAttachmentAsDataHandler(String cid) {
                    byte[] data = parts.get(strip(cid));
                    return data == null ? null : handler(data, types.get(strip(cid)));
                }
                @Override public boolean isXOPPackage() { return xop; }
            };
        }

        private String put(byte[] data, String type) {
            String cid = "part" + (++next) + "@simdxml";
            parts.put(cid, data); types.put(cid, type);
            return "cid:" + cid;
        }
        private static String strip(String cid) { return cid.startsWith("cid:") ? cid.substring(4) : cid; }
    }

    static DataHandler handler(final byte[] data, final String type) {
        return new DataHandler(new DataSource() {
            public InputStream getInputStream() { return new ByteArrayInputStream(data); }
            public OutputStream getOutputStream() { throw new UnsupportedOperationException(); }
            public String getContentType() { return type == null ? "application/octet-stream" : type; }
            public String getName() { return "part"; }
        });
    }

    private static byte[] bytes(DataHandler handler) throws IOException {
        try (InputStream in = handler.getInputStream()) { return in.readAllBytes(); }
    }

    private static String marshal(JAXBContext context, Object value, Store store) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setAttachmentMarshaller(store.marshaller());
        marshaller.marshal(value, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Object unmarshal(JAXBContext context, String xml, Store store) throws Exception {
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(store.unmarshaller());
        return unmarshaller.unmarshal(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void byteArrayRoundTripsThroughXopInclude() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {1, 2, 3, 4, 5};
        source.name = "invoice";

        String xml = marshal(context, source, store);
        assertTrue(xml.contains("<xop:Include"), xml);
        assertTrue(xml.contains("href=\"cid:part1@simdxml\""), xml);
        assertEquals(1, store.parts.size());

        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
        assertEquals("invoice", result.name);
    }

    @Test void declaredMimeTypeReachesTheAttachmentMarshaller() throws Exception {
        JAXBContext context = JAXBContext.newInstance(TypedDocument.class);
        Store store = new Store();
        TypedDocument source = new TypedDocument();
        source.image = new byte[] {9, 8, 7};

        marshal(context, source, store);
        assertEquals("image/png", store.types.get("part1@simdxml"));
    }

    @Test void dataHandlerIsPassedToTheProviderWithoutMaterializing() throws Exception {
        JAXBContext context = JAXBContext.newInstance(HandlerDocument.class);
        Store store = new Store();
        HandlerDocument source = new HandlerDocument();
        source.content = handler("hello attachment".getBytes(StandardCharsets.UTF_8), "text/plain");

        String xml = marshal(context, source, store);
        assertTrue(xml.contains("<xop:Include"), xml);
        assertEquals("text/plain", store.types.get("part1@simdxml"));

        HandlerDocument result = (HandlerDocument) unmarshal(context, xml, store);
        assertNotNull(result.content);
        assertEquals("hello attachment", new String(bytes(result.content), StandardCharsets.UTF_8));
        assertEquals("text/plain", result.content.getContentType());
    }

    @Test void inlineBinaryDataIsNeverOptimizedIntoAnAttachment() throws Exception {
        JAXBContext context = JAXBContext.newInstance(InlineDocument.class);
        Store store = new Store();
        InlineDocument source = new InlineDocument();
        source.payload = new byte[] {1, 2, 3};

        String xml = marshal(context, source, store);
        assertFalse(xml.contains("xop:Include"), xml);
        assertTrue(store.parts.isEmpty());
        assertTrue(xml.contains("AQID"), xml);

        InlineDocument result = (InlineDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
    }

    @Test void base64FallbackWhenTheMarshallerDoesNotEnableXop() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        store.xop = false;
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {10, 20, 30};

        String xml = marshal(context, source, store);
        assertFalse(xml.contains("xop:Include"), xml);
        assertTrue(store.parts.isEmpty());

        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
    }

    @Test void dataHandlerFallsBackToInlineBase64WithoutXop() throws Exception {
        JAXBContext context = JAXBContext.newInstance(HandlerDocument.class);
        Store store = new Store();
        store.xop = false;
        HandlerDocument source = new HandlerDocument();
        source.content = handler("plain".getBytes(StandardCharsets.UTF_8), "text/plain");

        String xml = marshal(context, source, store);
        assertFalse(xml.contains("xop:Include"), xml);
        assertTrue(xml.contains("cGxhaW4="), xml);

        HandlerDocument result = (HandlerDocument) unmarshal(context, xml, store);
        assertEquals("plain", new String(bytes(result.content), StandardCharsets.UTF_8));
    }

    @Test void attachmentRefBindsThroughSwaRef() throws Exception {
        JAXBContext context = JAXBContext.newInstance(SwaRefDocument.class);
        Store store = new Store();
        SwaRefDocument source = new SwaRefDocument();
        source.report = handler("report body".getBytes(StandardCharsets.UTF_8), "application/pdf");

        String xml = marshal(context, source, store);
        assertFalse(xml.contains("xop:Include"), xml);
        assertTrue(xml.contains("cid:part1@simdxml"), xml);
        assertEquals(1, store.parts.size());

        SwaRefDocument result = (SwaRefDocument) unmarshal(context, xml, store);
        assertNotNull(result.report);
        assertEquals("report body", new String(bytes(result.report), StandardCharsets.UTF_8));
    }

    @Test void xopIncludeDoesNotDesynchronizeTheFollowingElements() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        store.parts.put("one@simdxml", new byte[] {7, 7});
        store.types.put("one@simdxml", "application/octet-stream");
        String xml = "<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:one@simdxml\"/></payload><name>after</name></binary>";

        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(new byte[] {7, 7}, result.payload);
        // The sibling after the attachment is the regression this asserts: the binder used to leave
        // the enclosing </payload> unconsumed and bind "after" against the wrong frame.
        assertEquals("after", result.name);
    }

    @Test void xopIncludeIsBoundThroughTheStaxCursor() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        store.parts.put("one@simdxml", new byte[] {4, 5, 6});
        store.types.put("one@simdxml", "application/octet-stream");
        String xml = "<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:one@simdxml\"/></payload><name>stax</name></binary>";

        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(store.unmarshaller());
        javax.xml.stream.XMLStreamReader cursor = javax.xml.stream.XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(xml));
        BinaryDocument result = (BinaryDocument) unmarshaller.unmarshal(cursor);
        assertArrayEquals(new byte[] {4, 5, 6}, result.payload);
        assertEquals("stax", result.name);
    }

    @Test void xopIncludeIsBoundThroughTheSaxHandler() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        store.parts.put("one@simdxml", new byte[] {(byte) 200, 1});
        store.types.put("one@simdxml", "application/octet-stream");
        String xml = "<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:one@simdxml\"/></payload><name>sax</name></binary>";

        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(store.unmarshaller());
        jakarta.xml.bind.UnmarshallerHandler handler = unmarshaller.getUnmarshallerHandler();
        org.xml.sax.XMLReader reader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new org.xml.sax.InputSource(new StringReader(xml)));

        BinaryDocument result = (BinaryDocument) handler.getResult();
        assertArrayEquals(new byte[] {(byte) 200, 1}, result.payload);
        assertEquals("sax", result.name);
    }

    @Test void dataHandlerIsBoundThroughTheSaxHandler() throws Exception {
        JAXBContext context = JAXBContext.newInstance(HandlerDocument.class);
        Store store = new Store();
        store.parts.put("one@simdxml", "sax body".getBytes(StandardCharsets.UTF_8));
        store.types.put("one@simdxml", "text/plain");
        String xml = "<handler><content xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:one@simdxml\"/></content></handler>";

        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(store.unmarshaller());
        jakarta.xml.bind.UnmarshallerHandler handler = unmarshaller.getUnmarshallerHandler();
        org.xml.sax.XMLReader reader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new org.xml.sax.InputSource(new StringReader(xml)));

        HandlerDocument result = (HandlerDocument) handler.getResult();
        assertEquals("sax body", new String(bytes(result.content), StandardCharsets.UTF_8));
    }

    @Test void unknownContentIdBindsToNullRatherThanFailing() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        String xml = "<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"cid:missing@simdxml\"/></payload><name>x</name></binary>";
        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertNull(result.payload);
        assertEquals("x", result.name);
    }

    @Test void malformedXopHrefIsRejected() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        String xml = "<binary><payload xmlns:xop=\"http://www.w3.org/2004/08/xop/include\">"
                + "<xop:Include href=\"http://example.org/part\"/></payload></binary>";
        assertThrows(jakarta.xml.bind.JAXBException.class, () -> unmarshal(context, xml, store));
    }

    @Test void marshallingWithoutAnAttachmentMarshallerStillEmitsBase64() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {1, 2, 3};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        context.createMarshaller().marshal(source, out);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("AQID"));
    }

    @Test void xopIncludeIsWrittenByTheStaxWriter() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {3, 1, 4};
        source.name = "stax-out";

        StringWriter text = new StringWriter();
        javax.xml.stream.XMLStreamWriter writer = javax.xml.stream.XMLOutputFactory.newFactory()
                .createXMLStreamWriter(text);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setAttachmentMarshaller(store.marshaller());
        marshaller.marshal(source, writer);
        writer.flush();

        String xml = text.toString();
        assertTrue(xml.contains("Include"), xml);
        assertTrue(xml.contains("href=\"cid:part1@simdxml\""), xml);

        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
        assertEquals("stax-out", result.name);
    }

    @Test void xopIncludeIsWrittenByTheSaxWriter() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {2, 7, 1, 8};
        source.name = "sax-out";

        StringWriter text = new StringWriter();
        javax.xml.transform.sax.TransformerHandler handler =
                ((javax.xml.transform.sax.SAXTransformerFactory) javax.xml.transform.sax.SAXTransformerFactory.newInstance())
                        .newTransformerHandler();
        handler.setResult(new javax.xml.transform.stream.StreamResult(text));
        Marshaller marshaller = context.createMarshaller();
        marshaller.setAttachmentMarshaller(store.marshaller());
        marshaller.marshal(source, handler);

        String xml = text.toString();
        assertTrue(xml.contains("Include"), xml);
        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
        assertEquals("sax-out", result.name);
    }

    @XmlRootElement(name = "binary")
    public static final class BinaryDocument {
        @XmlElement public byte[] payload;
        @XmlElement public String name;
        public BinaryDocument() { }
    }

    @XmlRootElement(name = "typed")
    public static final class TypedDocument {
        @XmlElement @XmlMimeType("image/png") public byte[] image;
        public TypedDocument() { }
    }

    @XmlRootElement(name = "handler")
    public static final class HandlerDocument {
        @XmlElement public DataHandler content;
        public HandlerDocument() { }
    }

    @XmlRootElement(name = "inline")
    public static final class InlineDocument {
        @XmlElement @XmlInlineBinaryData public byte[] payload;
        public InlineDocument() { }
    }

    @XmlRootElement(name = "swaref")
    public static final class SwaRefDocument {
        @XmlElement @XmlAttachmentRef public DataHandler report;
        public SwaRefDocument() { }
    }
}
