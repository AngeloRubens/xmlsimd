package org.simdxml.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

final class SimdJaxbProviderTest {
    @Test void serviceLoaderProvidesStandardJaxbApiWithoutSimdxmlCalls() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Message.class);
        assertEquals(SimdJakartaContext.class, context.getClass());
        Message source = new Message(); source.id = 7; source.text = "SOAP & XML";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        context.createMarshaller().marshal(source, output);
        Message result = (Message) context.createUnmarshaller().unmarshal(new ByteArrayInputStream(output.toByteArray()));
        assertEquals(7, result.id); assertEquals("SOAP & XML", result.text);
    }

    @Test void supportsFragmentWriterAndStandardSourceInputs() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Message.class);
        var marshaller = context.createMarshaller(); marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        java.io.StringWriter output = new java.io.StringWriter();
        Message source = new Message(); source.id = 9; source.text = "value"; marshaller.marshal(source, output);
        assertFalse(output.toString().startsWith("<?xml"));
        Message result = (Message) context.createUnmarshaller().unmarshal(new StreamSource(new StringReader(output.toString())));
        assertEquals(9, result.id);
    }

    @Test void typedSourcePreservesExpandedRootName() throws Exception {
        JAXBContext context = JAXBContext.newInstance(NamespacedMessage.class);
        String xml = "<p:message xmlns:p='urn:messages'><p:text>namespaced</p:text></p:message>";
        var element = context.createUnmarshaller().unmarshal(
                new StreamSource(new StringReader(xml)), NamespacedMessage.class);
        assertEquals("urn:messages", element.getName().getNamespaceURI());
        assertEquals("message", element.getName().getLocalPart());
        assertEquals("namespaced", element.getValue().text);
    }

    @Test void delegatesColdPathSchemaGenerationToReferenceImplementation() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Message.class);
        java.io.StringWriter schema = new java.io.StringWriter();
        context.generateSchema(new jakarta.xml.bind.SchemaOutputResolver() {
            @Override public javax.xml.transform.Result createOutput(String namespace, String suggested) {
                javax.xml.transform.stream.StreamResult result = new javax.xml.transform.stream.StreamResult(schema);
                result.setSystemId(suggested); return result;
            }
        });
        assertTrue(schema.toString().contains("xs:schema"));
        assertTrue(schema.toString().contains("name=\"message\""));
    }

    @Test void marshalsJaxbElementOnNativeHotPath() throws Exception {
        String property = ColdPathJaxbDelegate.FACTORY_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "invalid.FactoryMustNotBeUsed");
        try {
            JAXBContext context = JAXBContext.newInstance(Message.class);
            jakarta.xml.bind.JAXBElement<String> element = new jakarta.xml.bind.JAXBElement<String>(
                    new javax.xml.namespace.QName("urn:simdxml:test", "provider"), String.class, "simdxml");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            context.createMarshaller().marshal(element, output);
            String xml = output.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(xml.contains("<provider xmlns=\"urn:simdxml:test\">simdxml</provider>"));
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
    }

    @Test void supportsDomAndStaxOutputApisWithoutJaxbFallback() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Message.class);
        Message source = new Message(); source.id = 21; source.text = "events";
        org.w3c.dom.Node node = context.createMarshaller().getNode(source);
        assertEquals("message", node.getNodeName());
        java.io.StringWriter text = new java.io.StringWriter();
        javax.xml.stream.XMLStreamWriter writer = javax.xml.stream.XMLOutputFactory.newFactory()
                .createXMLStreamWriter(text);
        String factoryProperty = "javax.xml.transform.TransformerFactory";
        String previousFactory = System.getProperty(factoryProperty);
        System.setProperty(factoryProperty, "invalid.FactoryMustNotBeUsedByStaxHotPath");
        try { context.createMarshaller().marshal(source, writer); }
        finally { if (previousFactory == null) System.clearProperty(factoryProperty); else System.setProperty(factoryProperty, previousFactory); }
        writer.close();
        assertTrue(text.toString().contains("<message"));
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
        JAXBContext context = JAXBContext.newInstance(Message.class);
        jakarta.xml.bind.UnmarshallerHandler handler = context.createUnmarshaller().getUnmarshallerHandler();
        org.xml.sax.XMLReader reader = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(handler);
        String key="javax.xml.transform.TransformerFactory",previous=System.getProperty(key);System.setProperty(key,"invalid.NotUsedBySaxBinder");
        try{reader.parse(new org.xml.sax.InputSource(new StringReader("<message id='31'><text>sax</text></message>")));}
        finally{if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
        Message value = (Message) handler.getResult();
        assertEquals(31, value.id); assertEquals("sax", value.text);
    }

    @Test void validatesMarshalWhenSchemaIsConfigured() throws Exception {
        String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
                + "<xs:element name='other' type='xs:string'/></xs:schema>";
        javax.xml.validation.Schema schema = javax.xml.validation.SchemaFactory
                .newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd)));
        Marshaller marshaller = JAXBContext.newInstance(Message.class).createMarshaller();
        marshaller.setSchema(schema);
        Message value = new Message(); value.text = "invalid root";
        assertThrows(jakarta.xml.bind.MarshalException.class,
                () -> marshaller.marshal(value, new ByteArrayOutputStream()));
    }

    @Test void appliesPrecompiledXmlAdapterInBothDirections() throws Exception {
        JAXBContext context = JAXBContext.newInstance(AdaptedMessage.class);
        CodeAdapter configured = new CodeAdapter();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setAdapter(CodeAdapter.class, configured);
        assertSame(configured, marshaller.getAdapter(CodeAdapter.class));
        AdaptedMessage source = new AdaptedMessage(); source.code = new Code("42");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); marshaller.marshal(source, output);
        assertTrue(output.toString(java.nio.charset.StandardCharsets.UTF_8).contains(">C:42<"));
        AdaptedMessage value = (AdaptedMessage) context.createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(output.toByteArray()));
        assertEquals("42", value.code.value);
    }
    @Test void bindsDirectlyFromBothStaxReaderApis() throws Exception {
        JAXBContext context=JAXBContext.newInstance(Message.class); String xml="<message id='51'><text>cursor</text></message>";
        String key="javax.xml.transform.TransformerFactory", previous=System.getProperty(key); System.setProperty(key,"invalid.NotUsed");
        try {
            javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));
            Message a=(Message)context.createUnmarshaller().unmarshal(cursor); assertEquals(51,a.id);
            javax.xml.stream.XMLEventReader events=javax.xml.stream.XMLInputFactory.newFactory().createXMLEventReader(new StringReader(xml));
            Message b=(Message)context.createUnmarshaller().unmarshal(events); assertEquals("cursor",b.text);
        } finally { if(previous==null)System.clearProperty(key);else System.setProperty(key,previous); }
    }
    @Test void roundTripsStandardXmlScalarTypes() throws Exception {
        StandardTypes source=new StandardTypes();source.binary=new byte[]{0,1,2,(byte)255};source.hex=new byte[]{10,31};source.uri=java.net.URI.create("urn:test:42");source.when=javax.xml.datatype.DatatypeFactory.newInstance().newXMLGregorianCalendar("2026-08-19T17:00:00Z");source.duration=javax.xml.datatype.DatatypeFactory.newInstance().newDuration("PT5M");source.status=Status.IN_PROGRESS;source.name=new javax.xml.namespace.QName("urn:types","kind","t");source.calendar=source.when.toGregorianCalendar();
        JAXBContext context=JAXBContext.newInstance(StandardTypes.class);ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(source,out);String xml=out.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(xml.contains("AAEC/w=="));assertTrue(xml.contains("0A1F"));assertTrue(xml.contains("in-progress"));
        StandardTypes value=(StandardTypes)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(out.toByteArray()));assertArrayEquals(source.binary,value.binary);assertArrayEquals(source.hex,value.hex);assertEquals(source.uri,value.uri);assertEquals(source.when,value.when);assertEquals(source.duration,value.duration);assertEquals(Status.IN_PROGRESS,value.status);assertEquals(source.name,value.name);assertEquals(source.calendar.getTimeInMillis(),value.calendar.getTimeInMillis());
    }
    @Test void standardTypeCodecsAreSafeForPlatformAndVirtualThreads() throws Exception {
        final JAXBContext context=JAXBContext.newInstance(StandardTypes.class);
        java.util.concurrent.ExecutorService platform=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{assertConcurrentTypeBinding(context,platform,32);}finally{platform.shutdown();}
        java.util.concurrent.ExecutorService virtual=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        try{assertConcurrentTypeBinding(context,virtual,64);}finally{virtual.shutdown();}
    }
    @Test void roundTripsWrappedCollections() throws Exception {
        JAXBContext context=JAXBContext.newInstance(WrappedValues.class);WrappedValues source=new WrappedValues();source.values.add("one");source.values.add("two");source.numbers=new int[]{3,5,8};ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(source,out);String xml=out.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(xml.contains("<values><value>one</value><value>two</value></values>"));assertTrue(xml.contains("<numbers><number>3</number><number>5</number><number>8</number></numbers>"));WrappedValues result=(WrappedValues)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(out.toByteArray()));assertEquals(source.values,result.values);assertArrayEquals(source.numbers,result.numbers);
        javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));WrappedValues stax=(WrappedValues)context.createUnmarshaller().unmarshal(cursor);assertArrayEquals(source.numbers,stax.numbers);
        jakarta.xml.bind.UnmarshallerHandler handler=context.createUnmarshaller().getUnmarshallerHandler();org.xml.sax.XMLReader sax=javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();sax.setContentHandler(handler);sax.parse(new org.xml.sax.InputSource(new StringReader(xml)));WrappedValues pushed=(WrappedValues)handler.getResult();assertEquals(source.values,pushed.values);assertArrayEquals(source.numbers,pushed.numbers);
    }
    @Test void handlesNilAndDefaultsOnStreamAndSaxPaths() throws Exception {
        String xml="<defaults xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'><optional xsi:nil='true'/><count/><required>ok</required></defaults>";JAXBContext context=JAXBContext.newInstance(DefaultsDocument.class);
        DefaultsDocument bytes=(DefaultsDocument)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));assertNull(bytes.optional);assertEquals(7,bytes.count);
        javax.xml.stream.XMLStreamReader cursor=javax.xml.stream.XMLInputFactory.newFactory().createXMLStreamReader(new StringReader(xml));DefaultsDocument stax=(DefaultsDocument)context.createUnmarshaller().unmarshal(cursor);assertEquals(7,stax.count);
        jakarta.xml.bind.UnmarshallerHandler handler=context.createUnmarshaller().getUnmarshallerHandler();org.xml.sax.XMLReader sax=javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getXMLReader();sax.setContentHandler(handler);sax.parse(new org.xml.sax.InputSource(new StringReader(xml)));DefaultsDocument pushed=(DefaultsDocument)handler.getResult();assertNull(pushed.optional);assertEquals(7,pushed.count);
        ByteArrayOutputStream out=new ByteArrayOutputStream();context.createMarshaller().marshal(bytes,out);assertTrue(out.toString(java.nio.charset.StandardCharsets.UTF_8).contains("xsi:nil=\"true\""));
    }
    private static void assertConcurrentTypeBinding(JAXBContext context,java.util.concurrent.ExecutorService executor,int count)throws Exception{
        java.util.List<java.util.concurrent.Future<Boolean>> futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
        for(int i=0;i<count;i++){final int value=i;futures.add(executor.submit(()->{String xml="<standard><when>2026-08-19T17:00:"+String.format("%02d",value%60)+"Z</when><duration>PT"+(value+1)+"S</duration></standard>";StandardTypes parsed=(StandardTypes)context.createUnmarshaller().unmarshal(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return parsed.when!=null&&parsed.duration!=null;}));}
        for(java.util.concurrent.Future<Boolean> future:futures)assertTrue(future.get());
    }

    @XmlRootElement(name = "message")
    public static final class Message {
        @XmlAttribute public int id;
        @XmlElement public String text;
        public Message() { }
    }
    @XmlRootElement(name = "message", namespace = "urn:messages")
    public static final class NamespacedMessage {
        @XmlElement(name = "text", namespace = "urn:messages") public String text;
        public NamespacedMessage() { }
    }
    @XmlRootElement(name = "adapted")
    public static final class AdaptedMessage {
        @XmlElement @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(CodeAdapter.class)
        public Code code;
        public AdaptedMessage() { }
    }
    public static final class Code { final String value; Code(String value) { this.value = value; } }
    public static final class CodeAdapter extends jakarta.xml.bind.annotation.adapters.XmlAdapter<String, Code> {
        @Override public Code unmarshal(String value) { return new Code(value.substring(2)); }
        @Override public String marshal(Code value) { return "C:" + value.value; }
    }
    @XmlRootElement(name="standard") public static final class StandardTypes{
        @XmlElement public byte[] binary;
        @XmlElement @jakarta.xml.bind.annotation.XmlSchemaType(name="hexBinary") public byte[] hex;
        @XmlElement public java.net.URI uri;
        @XmlElement public javax.xml.datatype.XMLGregorianCalendar when;
        @XmlElement public javax.xml.datatype.Duration duration;
        @XmlElement public Status status;
        @XmlElement public javax.xml.namespace.QName name;
        @XmlElement public java.util.Calendar calendar;
        public StandardTypes(){}
    }
    public enum Status{READY,@jakarta.xml.bind.annotation.XmlEnumValue("in-progress") IN_PROGRESS}
    @XmlRootElement(name="wrapped") public static final class WrappedValues{
        @jakarta.xml.bind.annotation.XmlElementWrapper(name="values") @XmlElement(name="value") public java.util.List<String> values=new java.util.ArrayList<String>();
        @jakarta.xml.bind.annotation.XmlElementWrapper(name="numbers") @XmlElement(name="number") public int[] numbers;
        public WrappedValues(){}
    }
    @XmlRootElement(name="defaults") public static final class DefaultsDocument{
        @XmlElement(nillable=true) public String optional;
        @XmlElement(defaultValue="7") public Integer count;
        @XmlElement(required=true) public String required;
        public DefaultsDocument(){}
    }
}
