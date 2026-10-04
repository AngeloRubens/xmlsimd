package org.simdxml.javax;

import org.junit.jupiter.api.Test;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttachmentRef;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlInlineBinaryData;
import javax.xml.bind.annotation.XmlMimeType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The Jakarta MTOM suite, ported to the javax provider; Java 8 source level. */
final class SimdMtomTest {
    private static final String UTF8 = "UTF-8";

    static final class Store {
        final Map<String, byte[]> parts = new HashMap<String, byte[]>();
        final Map<String, String> types = new HashMap<String, String>();
        boolean xop = true;
        int next;

        AttachmentMarshaller marshaller() {
            return new AttachmentMarshaller() {
                public String addMtomAttachment(DataHandler data, String ns, String local) {
                    return put(drain(data), data.getContentType());
                }
                public String addMtomAttachment(byte[] data, int offset, int length, String type, String ns, String local) {
                    byte[] copy = new byte[length];
                    System.arraycopy(data, offset, copy, 0, length);
                    return put(copy, type);
                }
                public String addSwaRefAttachment(DataHandler data) { return put(drain(data), data.getContentType()); }
                public boolean isXOPPackage() { return xop; }
            };
        }

        AttachmentUnmarshaller unmarshaller() {
            return new AttachmentUnmarshaller() {
                public byte[] getAttachmentAsByteArray(String cid) { return parts.get(strip(cid)); }
                public DataHandler getAttachmentAsDataHandler(String cid) {
                    byte[] data = parts.get(strip(cid));
                    return data == null ? null : handler(data, types.get(strip(cid)));
                }
                public boolean isXOPPackage() { return xop; }
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

    static byte[] drain(DataHandler handler) {
        try {
            InputStream in = handler.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = in.read(buffer)) >= 0; ) out.write(buffer, 0, read);
                return out.toByteArray();
            } finally { in.close(); }
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static String marshal(JAXBContext context, Object value, Store store) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setAttachmentMarshaller(store.marshaller());
        marshaller.marshal(value, out);
        return out.toString(UTF8);
    }

    private static Object unmarshal(JAXBContext context, String xml, Store store) throws Exception {
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setAttachmentUnmarshaller(store.unmarshaller());
        return unmarshaller.unmarshal(new ByteArrayInputStream(xml.getBytes(UTF8)));
    }

    @Test void byteArrayRoundTripsThroughXopInclude() throws Exception {
        JAXBContext context = JAXBContext.newInstance(BinaryDocument.class);
        Store store = new Store();
        BinaryDocument source = new BinaryDocument();
        source.payload = new byte[] {1, 2, 3, 4, 5};
        source.name = "invoice";

        String xml = marshal(context, source, store);
        assertTrue(xml.contains("<xop:Include"), xml);
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

    @Test void dataHandlerRoundTripsWithoutMaterializing() throws Exception {
        JAXBContext context = JAXBContext.newInstance(HandlerDocument.class);
        Store store = new Store();
        HandlerDocument source = new HandlerDocument();
        source.content = handler("hello attachment".getBytes(UTF8), "text/plain");

        String xml = marshal(context, source, store);
        assertTrue(xml.contains("<xop:Include"), xml);
        assertEquals("text/plain", store.types.get("part1@simdxml"));

        HandlerDocument result = (HandlerDocument) unmarshal(context, xml, store);
        assertNotNull(result.content);
        assertEquals("hello attachment", new String(drain(result.content), UTF8));
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
        BinaryDocument result = (BinaryDocument) unmarshal(context, xml, store);
        assertArrayEquals(source.payload, result.payload);
    }

    @Test void attachmentRefBindsThroughSwaRef() throws Exception {
        JAXBContext context = JAXBContext.newInstance(SwaRefDocument.class);
        Store store = new Store();
        SwaRefDocument source = new SwaRefDocument();
        source.report = handler("report body".getBytes(UTF8), "application/pdf");

        String xml = marshal(context, source, store);
        assertFalse(xml.contains("xop:Include"), xml);
        assertTrue(xml.contains("cid:part1@simdxml"), xml);

        SwaRefDocument result = (SwaRefDocument) unmarshal(context, xml, store);
        assertNotNull(result.report);
        assertEquals("report body", new String(drain(result.report), UTF8));
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
        javax.xml.bind.UnmarshallerHandler handler = unmarshaller.getUnmarshallerHandler();
        org.xml.sax.XMLReader reader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        reader.parse(new org.xml.sax.InputSource(new StringReader(xml)));

        BinaryDocument result = (BinaryDocument) handler.getResult();
        assertArrayEquals(new byte[] {(byte) 200, 1}, result.payload);
        assertEquals("sax", result.name);
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
