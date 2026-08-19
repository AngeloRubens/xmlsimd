package org.simdxml;

import com.ctc.wstx.stax.WstxInputFactory;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.XmlTransient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Specification-oriented compatibility gates. Cases use only standard XML/JAXB semantics and run
 * against independent reference implementations; no Jackson or Woodstox extension is exercised.
 */
final class StandardCompatibilityKitTest {
    private static final class XmlCase {
        private final String name, xml;
        private XmlCase(String name, String xml) { this.name = name; this.xml = xml; }
        String name() { return name; } String xml() { return xml; }
    }

    @TestFactory Stream<DynamicTest> secureStreamingProfileMatchesJdkStaxAndWoodstox() {
        List<XmlCase> cases = List.of(
                new XmlCase("empty-element", "<root><empty/></root>"),
                new XmlCase("attributes-and-entities", "<root a='1' b=\"x&amp;y\">A&lt;B&#33;</root>"),
                new XmlCase("unicode-utf8", "<root città='Roma'>Καλημέρα 世界</root>"),
                new XmlCase("namespaces-as-qualified-names", "<p:root xmlns:p='urn:test'><p:item>v</p:item></p:root>"),
                new XmlCase("cdata-comment-pi", "<?work test?><root><!--ignored--><![CDATA[a<b]]></root>"),
                new XmlCase("mixed-content", "<root>before<child>inside</child>after</root>"),
                new XmlCase("xml-declaration", "<?xml version='1.0' encoding='UTF-8'?><root>ok</root>")
        );
        return cases.stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> {
            String expected = staxDigest(XMLInputFactory.newDefaultFactory(), test.xml());
            assertEquals(expected, staxDigest(new WstxInputFactory(), test.xml()), "reference implementations disagree");
            assertEquals(expected, simdDigest(test.xml()));
        }));
    }

    @Test void jaxbUnmarshalBindingProfileMatchesReferenceImplementation() throws Exception {
        byte[] xml = ("<catalog edition='3'><book id='7'>SIMD</book><book id='8'>XML</book>"
                + "<metadata><owner>team</owner><ignored>forward-compatible</ignored></metadata></catalog>")
                .getBytes(StandardCharsets.UTF_8);
        SimdUnmarshaller simd = SimdJaxbContext.builder(Catalog.class).withCapacity(4096).build().createUnmarshaller();
        Catalog actual = simd.unmarshal(xml, Catalog.class);
        Catalog reference = (Catalog) JAXBContext.newInstance(Catalog.class).createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(xml));
        assertEquals(snapshot(reference), snapshot(actual));
    }

    @Test void jaxbMarshalBindingProfileRoundTripsLikeReferenceImplementation() throws Exception {
        Catalog source = new Catalog(); source.edition = 4;
        Book book = new Book(); book.id = 9; book.title = "SIMD & XML <fast> 世界";
        source.books = List.of(book); source.metadata = new Metadata(); source.metadata.owner = "team";
        SimdJaxbContext context = SimdJaxbContext.builder(Catalog.class).build();
        byte[] simdXml = context.createMarshaller().marshal(source);
        assertEquals(snapshot(source), snapshot(context.createUnmarshaller().unmarshal(simdXml, Catalog.class)));
        Catalog referenceRead = (Catalog) JAXBContext.newInstance(Catalog.class).createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(simdXml));
        assertEquals(snapshot(source), snapshot(referenceRead));

        ByteArrayOutputStream referenceXml = new ByteArrayOutputStream();
        Marshaller referenceMarshaller = JAXBContext.newInstance(Catalog.class).createMarshaller();
        referenceMarshaller.marshal(source, referenceXml);
        assertEquals(snapshot(source), snapshot(context.createUnmarshaller()
                .unmarshal(referenceXml.toByteArray(), Catalog.class)));
    }

    @Test void jaxbStandardScalarLexicalsInheritanceAndTransientMatchReference() throws Exception {
        byte[] xml = ("<scalars active='1'><longValue>9223372036854775806</longValue>"
                + "<decimal>1234567890.0123456789</decimal><positiveInfinity>INF</positiveInfinity>"
                + "<state>READY</state><hidden>must-not-bind</hidden></scalars>").getBytes(StandardCharsets.UTF_8);
        SimdJaxbContext context = SimdJaxbContext.builder(Scalars.class).build();
        Scalars actual = context.createUnmarshaller().unmarshal(xml, Scalars.class);
        Scalars reference = (Scalars) JAXBContext.newInstance(Scalars.class).createUnmarshaller()
                .unmarshal(new ByteArrayInputStream(xml));
        assertEquals(scalarSnapshot(reference), scalarSnapshot(actual));
        assertEquals("initial", actual.hidden);
        Scalars roundTrip = context.createUnmarshaller().unmarshal(context.createMarshaller().marshal(actual), Scalars.class);
        assertEquals(scalarSnapshot(actual), scalarSnapshot(roundTrip));
    }

    @Test void jaxbExpandedNamesMatchAcrossPrefixesAndRoundTripWithReference() throws Exception {
        byte[] xml = ("<o:order xmlns:o='urn:orders' xmlns:m='urn:meta' xmlns:p='urn:people' m:id='A-7'>"
                + "<p:customer><p:name>Ada</p:name></p:customer><note xmlns=''>fast</note></o:order>")
                .getBytes(StandardCharsets.UTF_8);
        SimdJaxbContext context = SimdJaxbContext.builder(NamespacedOrder.class).build();
        NamespacedOrder actual = context.createUnmarshaller().unmarshal(xml, NamespacedOrder.class);
        NamespacedOrder discovered = (NamespacedOrder) context.unmarshal(xml);
        NamespacedOrder reference = (NamespacedOrder) JAXBContext.newInstance(NamespacedOrder.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(xml));
        assertEquals(namespaceSnapshot(reference), namespaceSnapshot(actual));
        assertEquals(namespaceSnapshot(reference), namespaceSnapshot(discovered));

        byte[] simdXml = context.createMarshaller().marshal(actual);
        NamespacedOrder referenceRead = (NamespacedOrder) JAXBContext.newInstance(NamespacedOrder.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(simdXml));
        assertEquals(namespaceSnapshot(actual), namespaceSnapshot(referenceRead));
        assertEquals(namespaceSnapshot(actual), namespaceSnapshot(
                context.createUnmarshaller().unmarshal(simdXml, NamespacedOrder.class)));
    }

    @Test void jaxbPropertyAccessArraysAndCollectionsMatchReference() throws Exception {
        byte[] xml = ("<propertyGraph id='17'><node><name>A</name></node><node><name>B</name></node>"
                + "<label>fast</label><label>xml</label><score>3</score><score>5</score></propertyGraph>")
                .getBytes(StandardCharsets.UTF_8);
        SimdJaxbContext context = SimdJaxbContext.builder(PropertyGraph.class).build();
        PropertyGraph actual = context.createUnmarshaller().unmarshal(xml, PropertyGraph.class);
        PropertyGraph reference = (PropertyGraph) JAXBContext.newInstance(PropertyGraph.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(xml));
        assertEquals(propertySnapshot(reference), propertySnapshot(actual));
        byte[] output = context.createMarshaller().marshal(actual);
        PropertyGraph referenceRead = (PropertyGraph) JAXBContext.newInstance(PropertyGraph.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(output));
        assertEquals(propertySnapshot(actual), propertySnapshot(referenceRead));
    }

    @Test void jaxbPortableMapAdapterMatchesReference() throws Exception {
        MapDocument source = new MapDocument();
        source.values.put("alpha", 1); source.values.put("beta", 2);
        SimdJaxbContext context = SimdJaxbContext.builder(MapDocument.class).build();
        byte[] output = context.createMarshaller().marshal(source);
        MapDocument simd = context.createUnmarshaller().unmarshal(output, MapDocument.class);
        MapDocument reference = (MapDocument) JAXBContext.newInstance(MapDocument.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(output));
        assertEquals(source.values, simd.values);
        assertEquals(source.values, reference.values);

        ByteArrayOutputStream referenceXml = new ByteArrayOutputStream();
        JAXBContext.newInstance(MapDocument.class).createMarshaller().marshal(source, referenceXml);
        assertEquals(source.values, context.createUnmarshaller()
                .unmarshal(referenceXml.toByteArray(), MapDocument.class).values);
    }

    @Test void jaxbNilAndElementDefaultsMatchReference() throws Exception {
        byte[] xml=("<defaults xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>"
                + "<optional xsi:nil='true'/><count/><required>present</required></defaults>")
                .getBytes(StandardCharsets.UTF_8);
        SimdJaxbContext context=SimdJaxbContext.builder(DefaultsDocument.class).build();
        DefaultsDocument simd=context.createUnmarshaller().unmarshal(xml,DefaultsDocument.class);
        DefaultsDocument reference=(DefaultsDocument)JAXBContext.newInstance(DefaultsDocument.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(xml));
        assertEquals(reference.optional,simd.optional);assertEquals(reference.count,simd.count);
        assertEquals(reference.required,simd.required);
        byte[] output=context.createMarshaller().marshal(simd);
        assertEquals(true,new String(output,StandardCharsets.UTF_8).contains("xsi:nil=\"true\""));
        DefaultsDocument referenceRead=(DefaultsDocument)JAXBContext.newInstance(DefaultsDocument.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(output));
        assertEquals(simd.count,referenceRead.count);assertEquals(simd.optional,referenceRead.optional);
    }

    @Test void jaxbXsiTypeUsesPrecompiledSubtypeDispatch() throws Exception {
        byte[] xml=("<petDocument xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' "
                + "xmlns:p='urn:pets'><pet xsi:type='p:dogType'><name>Rex</name><bark>true</bark></pet></petDocument>")
                .getBytes(StandardCharsets.UTF_8);
        SimdJaxbContext context=SimdJaxbContext.builder(PetDocument.class,Pet.class,Dog.class).build();
        PetDocument actual=context.createUnmarshaller().unmarshal(xml,PetDocument.class);
        assertEquals(Dog.class,actual.pet.getClass());assertEquals("Rex",actual.pet.name);assertTrue(((Dog)actual.pet).bark);
        PetDocument reference=(PetDocument)JAXBContext.newInstance(PetDocument.class,Pet.class,Dog.class)
                .createUnmarshaller().unmarshal(new ByteArrayInputStream(xml));
        assertEquals(reference.pet.getClass(),actual.pet.getClass());
        PetDocument source=new PetDocument();Dog dog=new Dog();dog.name="Milo";dog.bark=true;source.pet=dog;
        byte[] marshalled=context.createMarshaller().marshal(source);String text=new String(marshalled,StandardCharsets.UTF_8);
        assertTrue(text.contains("xsi:type=\"t:dogType\"")||text.contains("xsi:type=\"dogType\""));
        PetDocument roundTrip=context.createUnmarshaller().unmarshal(marshalled,PetDocument.class);
        assertEquals(Dog.class,roundTrip.pet.getClass());assertEquals("Milo",roundTrip.pet.name);assertTrue(((Dog)roundTrip.pet).bark);
        java.io.StringWriter writer=new java.io.StringWriter();javax.xml.stream.XMLStreamWriter stream=javax.xml.stream.XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
        context.createMarshaller().marshal(source,stream);stream.flush();assertTrue(writer.toString().contains("xsi:type"));
    }

    private static String simdDigest(String xml) {
        SimdXmlStreamReader reader = new SimdXmlParser(4096, 32).stream(xml.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        StringBuilder pendingText = new StringBuilder();
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                flushText(result, pendingText);
                result.append("S:").append(reader.name()).append('[');
                reader.attributes().entrySet().stream()
                        .filter(e -> !e.getKey().equals("xmlns") && !e.getKey().startsWith("xmlns:"))
                        .sorted(java.util.Map.Entry.comparingByKey())
                        .forEach(e -> result.append(e.getKey()).append('=').append(e.getValue()).append(';'));
                result.append("]|");
            } else if (event == XmlEvent.END_ELEMENT) {
                flushText(result, pendingText);
                result.append("E:").append(reader.name()).append('|');
            } else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) pendingText.append(reader.text());
        }
        flushText(result, pendingText);
        return result.toString();
    }

    private static String staxDigest(XMLInputFactory factory, String xml) throws Exception {
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        StringBuilder result = new StringBuilder();
        StringBuilder pendingText = new StringBuilder();
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    flushText(result, pendingText);
                    result.append("S:").append(reader.getName()).append('[');
                    List<String> attributes = new ArrayList<>();
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String name = reader.getAttributeName(i).toString();
                        if (!name.equals("xmlns") && !name.startsWith("xmlns:")
                                && !name.contains("http://www.w3.org/2000/xmlns/"))
                            attributes.add(name + "=" + reader.getAttributeValue(i));
                    }
                    attributes.stream().sorted().forEach(a -> result.append(a).append(';'));
                    result.append("]|");
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    flushText(result, pendingText);
                    result.append("E:").append(reader.getName()).append('|');
                }
                else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && reader.getTextLength() != 0) pendingText.append(reader.getText());
            }
        } finally { reader.close(); }
        flushText(result, pendingText);
        return result.toString();
    }

    private static void flushText(StringBuilder result, StringBuilder pendingText) {
        if (pendingText.isEmpty()) return;
        result.append("T:").append(pendingText).append('|');
        pendingText.setLength(0);
    }

    private static String snapshot(Catalog value) {
        StringBuilder result = new StringBuilder().append(value.edition).append(':');
        for (Book book : value.books) result.append(book.id).append('=').append(book.title).append(';');
        return result.append(value.metadata.owner).toString();
    }

    @XmlRootElement(name = "catalog")
    public static final class Catalog {
        @XmlAttribute public int edition;
        @XmlElement(name = "book") public List<Book> books;
        @XmlElement public Metadata metadata;
        public Catalog() { }
    }
    public static final class Book {
        @XmlAttribute public long id;
        @XmlValue public String title;
        public Book() { }
    }
    public static final class Metadata {
        @XmlElement public String owner;
        public Metadata() { }
    }
    @XmlRootElement(name = "mapDocument")
    public static final class MapDocument {
        @jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(StringIntegerMapAdapter.class)
        @XmlElement(name = "values")
        public java.util.Map<String, Integer> values = new java.util.LinkedHashMap<String, Integer>();
        public MapDocument() { }
    }
    @XmlRootElement(name="defaults")
    public static final class DefaultsDocument {
        @XmlElement(nillable=true) public String optional;
        @XmlElement(defaultValue="7") public Integer count;
        @XmlElement(required=true) public String required;
        public DefaultsDocument() { }
    }
    @XmlRootElement(name="petDocument") public static final class PetDocument {
        @XmlElement public Pet pet;
        public PetDocument() { }
    }
    @jakarta.xml.bind.annotation.XmlSeeAlso(Dog.class)
    @jakarta.xml.bind.annotation.XmlType(name="petType",namespace="urn:pets")
    public static class Pet {
        @XmlElement public String name;
        public Pet() { }
    }
    @jakarta.xml.bind.annotation.XmlType(name="dogType",namespace="urn:pets")
    public static final class Dog extends Pet {
        @XmlElement public boolean bark;
        public Dog() { }
    }
    public static final class MapEntries {
        @XmlElement(name = "entry") public java.util.List<MapEntry> entries = new java.util.ArrayList<MapEntry>();
        public MapEntries() { }
    }
    public static final class MapEntry {
        @XmlElement public String key;
        @XmlElement public int value;
        public MapEntry() { }
        MapEntry(String key, int value) { this.key = key; this.value = value; }
    }
    public static final class StringIntegerMapAdapter extends
            jakarta.xml.bind.annotation.adapters.XmlAdapter<MapEntries, java.util.Map<String, Integer>> {
        @Override public java.util.Map<String, Integer> unmarshal(MapEntries value) {
            java.util.Map<String, Integer> result = new java.util.LinkedHashMap<String, Integer>();
            if (value != null) for (MapEntry entry : value.entries) result.put(entry.key, entry.value);
            return result;
        }
        @Override public MapEntries marshal(java.util.Map<String, Integer> value) {
            MapEntries result = new MapEntries();
            if (value != null) for (java.util.Map.Entry<String, Integer> entry : value.entrySet())
                result.entries.add(new MapEntry(entry.getKey(), entry.getValue()));
            return result;
        }
    }
    @XmlRootElement(name = "order", namespace = "urn:orders")
    public static final class NamespacedOrder {
        @XmlAttribute(name = "id", namespace = "urn:meta") public String id;
        @XmlElement(name = "customer", namespace = "urn:people") public NamespacedCustomer customer;
        @XmlElement(name = "note", namespace = "") public String note;
        public NamespacedOrder() { }
    }
    public static final class NamespacedCustomer {
        @XmlElement(name = "name", namespace = "urn:people") public String name;
        public NamespacedCustomer() { }
    }
    @XmlRootElement(name = "propertyGraph")
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static final class PropertyGraph {
        private int id;
        private PropertyNode[] nodes;
        private java.util.Set<String> labels;
        private java.util.List<Integer> scores;
        public PropertyGraph() { }
        @XmlAttribute public int getId() { return id; }
        public void setId(int value) { id = value; }
        @XmlElement(name = "node") public PropertyNode[] getNodes() { return nodes; }
        public void setNodes(PropertyNode[] value) { nodes = value; }
        @XmlElement(name = "label") public java.util.Set<String> getLabels() { return labels; }
        public void setLabels(java.util.Set<String> value) { labels = value; }
        @XmlElement(name = "score") public java.util.List<Integer> getScores() { return scores; }
        public void setScores(java.util.List<Integer> value) { scores = value; }
    }
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static final class PropertyNode {
        private String name;
        public PropertyNode() { }
        @XmlElement public String getName() { return name; }
        public void setName(String value) { name = value; }
    }
    public enum State { READY, STOPPED }
    public static class ScalarBase {
        @XmlAttribute public boolean active;
        public ScalarBase() { }
    }
    @XmlRootElement(name = "scalars")
    public static final class Scalars extends ScalarBase {
        @XmlElement public long longValue;
        @XmlElement public java.math.BigDecimal decimal;
        @XmlElement public double positiveInfinity;
        @XmlElement public State state;
        @XmlTransient public String hidden = "initial";
        public Scalars() { }
    }
    private static String scalarSnapshot(Scalars value) {
        return value.active + ":" + value.longValue + ":" + value.decimal + ":"
                + value.positiveInfinity + ":" + value.state + ":" + value.hidden;
    }
    private static String namespaceSnapshot(NamespacedOrder value) {
        return value.id + ":" + value.customer.name + ":" + value.note;
    }
    private static String propertySnapshot(PropertyGraph value) {
        StringBuilder result = new StringBuilder().append(value.getId()).append(':');
        for (PropertyNode node : value.getNodes()) result.append(node.getName()).append(',');
        return result.append(':').append(value.getLabels()).append(':').append(value.getScores()).toString();
    }
}
