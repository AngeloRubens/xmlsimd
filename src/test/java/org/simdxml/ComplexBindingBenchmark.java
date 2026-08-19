package org.simdxml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Sequential complex-graph benchmark: arrays, List, Set and JAXB-portable map entries. */
public final class ComplexBindingBenchmark {
    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "simd-unmarshal" : args[0];
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        int width = args.length > 2 ? Integer.parseInt(args[2]) : 16;
        Graph source = graph(width);
        byte[] xml = referenceXml(source);
        Body body = body(mode, xml.length, source);
        long checksum = 0;
        for (int i = 0; i < 2_000; i++) checksum = Long.rotateLeft(checksum, 5) ^ body.run(xml);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 5) ^ body.run(xml);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT,
                "complex-binding=%s width=%d size=%d bytes operations/s=%.2f ns/operation=%.1f checksum=%d%n",
                mode, width, xml.length, iterations / seconds, seconds * 1e9 / iterations, checksum);
    }

    private static Body body(String mode, int capacity, Graph source) throws Exception {
        if ("simd-unmarshal".equals(mode)) {
            SimdUnmarshaller unmarshaller = SimdJaxbContext.builder(Graph.class).withCapacity(capacity).build().createUnmarshaller();
            return xml -> checksum(unmarshaller.unmarshal(xml, Graph.class));
        }
        if ("jaxb-unmarshal".equals(mode)) {
            Unmarshaller unmarshaller = JAXBContext.newInstance(Graph.class).createUnmarshaller();
            return xml -> checksum((Graph) unmarshaller.unmarshal(new ByteArrayInputStream(xml)));
        }
        if ("simd-marshal".equals(mode)) {
            SimdMarshaller marshaller = SimdJaxbContext.builder(Graph.class).build().createMarshaller();
            ByteArrayOutputStream output = new ByteArrayOutputStream(capacity);
            long semanticChecksum = checksum(source);
            return xml -> { output.reset(); marshaller.marshal(source, output); return semanticChecksum; };
        }
        if ("jaxb-marshal".equals(mode)) {
            Marshaller marshaller = JAXBContext.newInstance(Graph.class).createMarshaller();
            ByteArrayOutputStream output = new ByteArrayOutputStream(capacity);
            long semanticChecksum = checksum(source);
            return xml -> { output.reset(); marshaller.marshal(source, output); return semanticChecksum; };
        }
        throw new IllegalArgumentException("Unknown mode: " + mode);
    }

    private static Graph graph(int width) {
        Graph graph = new Graph(); graph.setId(42);
        Node[] nodes = new Node[width];
        List<String> tags = new ArrayList<String>();
        Set<Long> codes = new LinkedHashSet<Long>();
        List<Entry> entries = new ArrayList<Entry>();
        for (int i = 0; i < width; i++) {
            Node node = new Node(); node.setName("node-" + i); node.setValues(new int[]{i, i + 1, i + 2}); nodes[i] = node;
            tags.add("tag-" + i); codes.add(Long.valueOf(1000L + i));
            Entry entry = new Entry(); entry.setKey("key-" + i); entry.setValue("value-" + i); entries.add(entry);
        }
        graph.setNodes(nodes); graph.setTags(tags); graph.setCodes(codes); graph.setEntries(entries);
        return graph;
    }

    private static byte[] referenceXml(Graph source) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JAXBContext.newInstance(Graph.class).createMarshaller().marshal(source, output);
        return output.toByteArray();
    }

    private static long checksum(Graph graph) {
        long hash = graph.getId();
        for (Node node : graph.getNodes()) {
            hash = hash * 31 + node.getName().length();
            for (int value : node.getValues()) hash = hash * 31 + value;
        }
        for (String tag : graph.getTags()) hash = hash * 31 + tag.length();
        for (Long code : graph.getCodes()) hash = hash * 31 + code.longValue();
        Map<String, String> map = graph.asMap();
        for (Map.Entry<String, String> entry : map.entrySet())
            hash = hash * 31 + entry.getKey().length() + entry.getValue().length();
        return hash;
    }

    private interface Body { long run(byte[] xml) throws Exception; }

    @XmlRootElement(name = "graph") @XmlAccessorType(XmlAccessType.PROPERTY)
    public static final class Graph {
        private int id; private Node[] nodes; private List<String> tags;
        private Set<Long> codes; private List<Entry> entries;
        public Graph() { }
        @XmlAttribute public int getId() { return id; } public void setId(int value) { id = value; }
        @XmlElement(name = "node") public Node[] getNodes() { return nodes; } public void setNodes(Node[] value) { nodes = value; }
        @XmlElement(name = "tag") public List<String> getTags() { return tags; } public void setTags(List<String> value) { tags = value; }
        @XmlElement(name = "code") public Set<Long> getCodes() { return codes; } public void setCodes(Set<Long> value) { codes = value; }
        @XmlElement(name = "entry") public List<Entry> getEntries() { return entries; } public void setEntries(List<Entry> value) { entries = value; }
        public Map<String, String> asMap() {
            Map<String, String> result = new LinkedHashMap<String, String>();
            for (Entry entry : entries) result.put(entry.getKey(), entry.getValue());
            return result;
        }
    }
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static final class Node {
        private String name; private int[] values;
        public Node() { }
        @XmlElement public String getName() { return name; } public void setName(String value) { name = value; }
        @XmlElement(name = "value") public int[] getValues() { return values; } public void setValues(int[] value) { values = value; }
    }
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static final class Entry {
        private String key, value;
        public Entry() { }
        @XmlElement public String getKey() { return key; } public void setKey(String value) { key = value; }
        @XmlElement public String getValue() { return value; } public void setValue(String value) { this.value = value; }
    }
    private ComplexBindingBenchmark() { }
}
