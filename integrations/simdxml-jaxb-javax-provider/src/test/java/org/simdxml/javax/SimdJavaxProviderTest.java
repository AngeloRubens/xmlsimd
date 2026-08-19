package org.simdxml.javax;

import org.junit.jupiter.api.Test;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import javax.xml.transform.stream.StreamSource;
import static org.junit.jupiter.api.Assertions.*;

/** Java EE 8 compatibility test: application code imports javax only. */
final class SimdJavaxProviderTest {
    @Test void serviceLoaderBindsLegacyJavaxAnnotatedBeans() throws Exception {
        JAXBContext context = JAXBContext.newInstance(LegacyMessage.class);
        assertEquals(SimdJakartaContext.class, context.getClass());
        LegacyMessage source = new LegacyMessage(); source.id = 12; source.text = "WebLogic & Java EE 8";
        ByteArrayOutputStream xml = new ByteArrayOutputStream();
        context.createMarshaller().marshal(source, xml);
        LegacyMessage result = (LegacyMessage) context.createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(xml.toByteArray()));
        assertEquals(12, result.id); assertEquals("WebLogic & Java EE 8", result.text);
    }

    @Test void legacyTypedSourcePreservesExpandedRootName() throws Exception {
        JAXBContext context = JAXBContext.newInstance(LegacyNamespacedMessage.class);
        String xml = "<p:message xmlns:p='urn:legacy'><p:text>namespaced</p:text></p:message>";
        javax.xml.bind.JAXBElement<LegacyNamespacedMessage> element = context.createUnmarshaller()
                .unmarshal(new StreamSource(new StringReader(xml)), LegacyNamespacedMessage.class);
        assertEquals("urn:legacy", element.getName().getNamespaceURI());
        assertEquals("message", element.getName().getLocalPart());
        assertEquals("namespaced", element.getValue().text);
    }

    @Test void delegatesColdPathSchemaGenerationToReferenceImplementation() throws Exception {
        JAXBContext context = JAXBContext.newInstance(LegacyMessage.class);
        java.io.StringWriter schema = new java.io.StringWriter();
        context.generateSchema(new javax.xml.bind.SchemaOutputResolver() {
            @Override public javax.xml.transform.Result createOutput(String namespace, String suggested) {
                javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(schema);
                result.setSystemId(suggested); return result;
            }
        });
        assertTrue(schema.toString().contains("xs:schema"));
        assertTrue(schema.toString().contains("name=\"legacyMessage\""));
    }

    @Test void marshalsJaxbElementOnNativeHotPath() throws Exception {
        String property = ColdPathJaxbDelegate.FACTORY_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "invalid.FactoryMustNotBeUsed");
        try {
            JAXBContext context = JAXBContext.newInstance(LegacyMessage.class);
            javax.xml.bind.JAXBElement<String> element = new javax.xml.bind.JAXBElement<String>(
                    new javax.xml.namespace.QName("urn:simdxml:test", "provider"), String.class, "simdxml");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            context.createMarshaller().marshal(element, output);
            String xml = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("<provider xmlns=\"urn:simdxml:test\">simdxml</provider>"));
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
    }

    @Test void supportsDomAndStaxOutputApisWithoutJaxbFallback() throws Exception {
        JAXBContext context = JAXBContext.newInstance(LegacyMessage.class);
        LegacyMessage source = new LegacyMessage(); source.id = 22; source.text = "events";
        org.w3c.dom.Node node = context.createMarshaller().getNode(source);
        assertEquals("legacyMessage", node.getNodeName());
        java.io.StringWriter text = new java.io.StringWriter();
        javax.xml.stream.XMLStreamWriter writer = javax.xml.stream.XMLOutputFactory.newFactory()
                .createXMLStreamWriter(text);
        String factoryProperty = "javax.xml.transform.TransformerFactory";
        String previousFactory = System.getProperty(factoryProperty);
        System.setProperty(factoryProperty, "invalid.FactoryMustNotBeUsedByStaxHotPath");
        try { context.createMarshaller().marshal(source, writer); }
        finally { if (previousFactory == null) System.clearProperty(factoryProperty); else System.setProperty(factoryProperty, previousFactory); }
        writer.close();
        assertTrue(text.toString().contains("<legacyMessage"));
        assertTrue(text.toString().contains("events"));
        java.io.StringWriter eventText = new java.io.StringWriter();
        javax.xml.stream.XMLEventWriter eventWriter = javax.xml.stream.XMLOutputFactory.newFactory().createXMLEventWriter(eventText);
        System.setProperty(factoryProperty, "invalid.FactoryMustNotBeUsedByEventHotPath");
        try { context.createMarshaller().marshal(source, eventWriter); }
        finally { if (previousFactory == null) System.clearProperty(factoryProperty); else System.setProperty(factoryProperty, previousFactory); }
        eventWriter.close(); assertTrue(eventText.toString().contains("events"));
        final StringBuilder saxText = new StringBuilder();
        org.xml.sax.helpers.DefaultHandler sax = new org.xml.sax.helpers.DefaultHandler() {
            @Override public void characters(char[] ch, int start, int length) { saxText.append(ch, start, length); }
        };
        context.createMarshaller().marshal(source, sax);
        assertTrue(saxText.toString().contains("events"));
    }

    @Test void supportsSaxUnmarshallerHotPath() throws Exception {
        JAXBContext context = JAXBContext.newInstance(LegacyMessage.class);
        javax.xml.bind.UnmarshallerHandler handler = context.createUnmarshaller().getUnmarshallerHandler();
        org.xml.sax.XMLReader reader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        String key="javax.xml.transform.TransformerFactory",previous=System.getProperty(key);System.setProperty(key,"invalid.NotUsedBySaxBinder");
        try{reader.parse(new org.xml.sax.InputSource(new StringReader("<legacyMessage id='32'><text>sax</text></legacyMessage>")));}
        finally{if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
        LegacyMessage value = (LegacyMessage) handler.getResult();
        assertEquals(32, value.id); assertEquals("sax", value.text);
    }

    @Test void validatesMarshalWhenSchemaIsConfigured() throws Exception {
        String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
                + "<xs:element name='other' type='xs:string'/></xs:schema>";
        javax.xml.validation.Schema schema = javax.xml.validation.SchemaFactory
                .newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd)));
        javax.xml.bind.Marshaller marshaller = JAXBContext.newInstance(LegacyMessage.class).createMarshaller();
        marshaller.setSchema(schema);
        LegacyMessage value = new LegacyMessage(); value.text = "invalid root";
        assertThrows(javax.xml.bind.MarshalException.class,
                () -> marshaller.marshal(value, new ByteArrayOutputStream()));
    }

    @Test void appliesPrecompiledXmlAdapterInBothDirections() throws Exception {
        JAXBContext context = JAXBContext.newInstance(AdaptedMessage.class);
        CodeAdapter configured = new CodeAdapter();
        javax.xml.bind.Marshaller marshaller = context.createMarshaller();
        marshaller.setAdapter(CodeAdapter.class, configured);
        assertSame(configured, marshaller.getAdapter(CodeAdapter.class));
        AdaptedMessage source = new AdaptedMessage(); source.code = new Code("43");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); marshaller.marshal(source, output);
        assertTrue(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains(">C:43<"));
        AdaptedMessage value = (AdaptedMessage) context.createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(output.toByteArray()));
        assertEquals("43", value.code.value);
    }
    @Test void bindsDirectlyFromBothStaxReaderApis() throws Exception {
        JAXBContext context=JAXBContext.newInstance(LegacyMessage.class); String xml="<legacyMessage id='52'><text>cursor</text></legacyMessage>";
        String key="javax.xml.transform.TransformerFactory", previous=System.getProperty(key); System.setProperty(key,"invalid.NotUsed");
        try {
            javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
            LegacyMessage a=(LegacyMessage)context.createUnmarshaller().unmarshal(cursor); assertEquals(52,a.id);
            javax.xml.stream.XMLEventReader events=javax.xml.stream.XMLInputFactory.newFactory().createXMLEventReader(new StringReader(xml));
            LegacyMessage b=(LegacyMessage)context.createUnmarshaller().unmarshal(events); assertEquals("cursor",b.text);
        } finally { if(previous==null)System.clearProperty(key);else System.setProperty(key,previous); }
    }
    @Test void roundTripsStandardXmlScalarTypes() throws Exception {
        StandardTypes source=new StandardTypes();source.binary=new byte[]{0,1,2,(byte)255};source.hex=new byte[]{10,31};source.uri=java.net.URI.create("urn:test:43");source.when=javax.xml.datatype.DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-08-19T17:00:00Z");source.duration=javax.xml.datatype.DatatypeFactory.newInstance().newDuration("PT5M");source.status=Status.IN_PROGRESS;source.name=new javax.xml.namespace.QName("urn:types","kind","t");source.calendar=source.when.toGregorianCalendar();
        JAXBContext context=JAXBContext.newInstance(StandardTypes.class);ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(source,out);String xml=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(xml.contains("AAEC/w=="));assertTrue(xml.contains("0A1F"));assertTrue(xml.contains("in-progress"));
        StandardTypes value=(StandardTypes)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(out.toByteArray()));assertArrayEquals(source.binary,value.binary);assertArrayEquals(source.hex,value.hex);assertEquals(source.uri,value.uri);assertEquals(source.when,value.when);assertEquals(source.duration,value.duration);assertEquals(Status.IN_PROGRESS,value.status);assertEquals(source.name,value.name);assertEquals(source.calendar.getTimeInMillis(),value.calendar.getTimeInMillis());
    }
    @Test void roundTripsWrappedCollections() throws Exception {
        JAXBContext context=JAXBContext.newInstance(WrappedValues.class);WrappedValues source=new WrappedValues();source.values.add("one");source.values.add("two");source.numbers=new int[]{3,5,8};ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(source,out);String xml=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(xml.contains("<values><value>one</value><value>two</value></values>"));assertTrue(xml.contains("<numbers><number>3</number><number>5</number><number>8</number></numbers>"));WrappedValues result=(WrappedValues)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(out.toByteArray()));assertEquals(source.values,result.values);assertArrayEquals(source.numbers,result.numbers);
        javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));WrappedValues stax=(WrappedValues)context.createUnmarshaller().unmarshal(cursor);assertArrayEquals(source.numbers,stax.numbers);
        javax.xml.bind.UnmarshallerHandler handler=context.createUnmarshaller().getUnmarshallerHandler();org.xml.sax.XMLReader sax=javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();sax.setContentHandler(handler);sax.parse(new org.xml.sax.InputSource(new StringReader(xml)));WrappedValues pushed=(WrappedValues)handler.getResult();assertEquals(source.values,pushed.values);assertArrayEquals(source.numbers,pushed.numbers);
    }
    @Test void handlesNilAndDefaultsOnStreamAndSaxPaths() throws Exception {
        String xml="<defaults xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'><optional xsi:nil='true'/><count/><required>ok</required></defaults>";JAXBContext context=JAXBContext.newInstance(DefaultsDocument.class);
        DefaultsDocument bytes=(DefaultsDocument)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertNull(bytes.optional);assertEquals(7,bytes.count);
        javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));DefaultsDocument stax=(DefaultsDocument)context.createUnmarshaller().unmarshal(cursor);assertEquals(7,stax.count);
        javax.xml.bind.UnmarshallerHandler handler=context.createUnmarshaller().getUnmarshallerHandler();org.xml.sax.XMLReader sax=javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();sax.setContentHandler(handler);sax.parse(new org.xml.sax.InputSource(new StringReader(xml)));DefaultsDocument pushed=(DefaultsDocument)handler.getResult();assertNull(pushed.optional);assertEquals(7,pushed.count);
        ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(bytes,out);assertTrue(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).contains("xsi:nil=\"true\""));
    }

    @XmlRootElement(name = "legacyMessage")
    public static final class LegacyMessage {
        @XmlAttribute public int id;
        @XmlElement public String text;
        public LegacyMessage() { }
    }
    @XmlRootElement(name = "message", namespace = "urn:legacy")
    public static final class LegacyNamespacedMessage {
        @XmlElement(name = "text", namespace = "urn:legacy") public String text;
        public LegacyNamespacedMessage() { }
    }
    @XmlRootElement(name = "adapted")
    public static final class AdaptedMessage {
        @XmlElement @javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter(CodeAdapter.class)
        public Code code;
        public AdaptedMessage() { }
    }
    public static final class Code { final String value; Code(String value) { this.value = value; } }
    public static final class CodeAdapter extends javax.xml.bind.annotation.adapters.XmlAdapter<String, Code> {
        @Override public Code unmarshal(String value) { return new Code(value.substring(2)); }
        @Override public String marshal(Code value) { return "C:" + value.value; }
    }
    @XmlRootElement(name="standard") public static final class StandardTypes{
        @XmlElement public byte[] binary;
        @XmlElement @javax.xml.bind.annotation.XmlSchemaType(name="hexBinary") public byte[] hex;
        @XmlElement public java.net.URI uri;
        @XmlElement public javax.xml.datatype.XMLGregorianCalendar when;
        @XmlElement public javax.xml.datatype.Duration duration;
        @XmlElement public Status status;
        @XmlElement public javax.xml.namespace.QName name;
        @XmlElement public java.util.Calendar calendar;
        public StandardTypes(){}
    }
    public enum Status{READY,@javax.xml.bind.annotation.XmlEnumValue("in-progress") IN_PROGRESS}
    @XmlRootElement(name="wrapped") public static final class WrappedValues{
        @javax.xml.bind.annotation.XmlElementWrapper(name="values") @XmlElement(name="value") public java.util.List<String> values=new java.util.ArrayList<String>();
        @javax.xml.bind.annotation.XmlElementWrapper(name="numbers") @XmlElement(name="number") public int[] numbers;
        public WrappedValues(){}
    }
    @XmlRootElement(name="defaults") public static final class DefaultsDocument{
        @XmlElement(nillable=true) public String optional;
        @XmlElement(defaultValue="7") public Integer count;
        @XmlElement(required=true) public String required;
        public DefaultsDocument(){}
    }
}
