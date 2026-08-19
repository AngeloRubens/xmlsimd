package org.simdxml.compatibility.w3c;

import org.junit.jupiter.api.Test;
import org.simdxml.SimdXmlParser;
import org.simdxml.SimdXmlStreamReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bounded, deterministic engineering gate; it is not a W3C conformance claim. */
final class W3cXmlRepresentativeTest {
    private static final int CORE_LIMIT_PER_TYPE = 64;
    private static final int NAMESPACE_ORACLE_LIMIT_PER_TYPE = 24;

    @Test void representativeXml10GateAndCapabilityInventory() throws Exception {
        Path corpus = Paths.get(requiredProperty("org.simdxml.w3c.xmlts")).toRealPath();
        List<Case> cases = loadCatalog(corpus.resolve("xmlconf.xml"));
        Set<String> knownGaps = loadKnownGaps();
        Map<String, Integer> inventory = new LinkedHashMap<String, Integer>();
        Map<String, Integer> selected = new LinkedHashMap<String, Integer>();
        List<String> failures = new ArrayList<String>();
        List<String> rows = new ArrayList<String>();
        int observedKnownGaps = 0;
        rows.add("id\ttype\trecommendation\tclassification\toracle\tsimdxml\turi");

        for (Case test : cases) {
            byte[] bytes = Files.readAllBytes(test.path);
            String classification = classify(test, bytes);
            increment(inventory, classification);
            boolean core = "CORE_XML10_UTF8".equals(classification)
                    && reserve(selected, "core-" + test.type, CORE_LIMIT_PER_TYPE);
            boolean namespaceOracle = "UNSUPPORTED_NAMESPACE_CONFORMANCE".equals(classification)
                    && ("valid".equals(test.type) || "not-wf".equals(test.type))
                    && reserve(selected, "namespace-oracle-" + test.type, NAMESPACE_ORACLE_LIMIT_PER_TYPE);
            String oracle = "NOT_RUN";
            String simd = "NOT_RUN";
            if (core || namespaceOracle) {
                boolean accepted = acceptsWithJdkStax(test, bytes);
                oracle = accepted ? "ACCEPT" : "REJECT";
                if (core && expectedAccept(test.type) != accepted)
                    failures.add(test.id + " JDK StAX expected " + expected(test.type) + " but was " + oracle);
            }
            if (core) {
                boolean accepted = acceptsWithSimdxml(bytes);
                simd = accepted ? "ACCEPT" : "REJECT";
                if (expectedAccept(test.type) != accepted) {
                    if (knownGaps.contains(test.id)) { simd = "KNOWN_GAP_" + simd; observedKnownGaps++; }
                    else failures.add(test.id + " simdxml expected " + expected(test.type) + " but was " + simd);
                }
            }
            rows.add(tsv(test.id) + '\t' + test.type + '\t' + test.recommendation + '\t'
                    + classification + '\t' + oracle + '\t' + simd + '\t' + tsv(test.path.toString()));
        }

        Path target = Paths.get("target");
        Files.createDirectories(target);
        Files.write(target.resolve("w3c-xmlts-results.tsv"), rows, StandardCharsets.UTF_8);
        List<String> summary = new ArrayList<String>();
        summary.add("W3C XML Test Suite 20130923 - representative unofficial engineering gate");
        summary.add("catalog_cases=" + cases.size());
        for (Map.Entry<String, Integer> entry : inventory.entrySet())
            summary.add("classified." + entry.getKey() + '=' + entry.getValue());
        for (Map.Entry<String, Integer> entry : selected.entrySet())
            summary.add("executed." + entry.getKey() + '=' + entry.getValue());
        summary.add("failures=" + failures.size());
        summary.add("observed_known_gaps=" + observedKnownGaps);
        Files.write(target.resolve("w3c-xmlts-summary.txt"), summary, StandardCharsets.UTF_8);
        assertTrue(failures.isEmpty(), joinFailures(failures));
    }

    @Test void supportedWellFormedUtf8Smoke() {
        String[] documents = {
                "<?xml version='1.0'?><root/>",
                "<root a='x&amp;y'>Καλημέρα 世界</root>",
                "<p:root xmlns:p='urn:smoke'><p:item><![CDATA[a<b]]></p:item></p:root>"
        };
        for (String document : documents)
            assertTrue(acceptsWithSimdxml(document.getBytes(StandardCharsets.UTF_8)), document);
    }

    private static Set<String> loadKnownGaps() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("src/test/resources/w3c-xmlts-known-gaps.txt"), StandardCharsets.UTF_8);
        Set<String> result = new HashSet<String>();
        for (String line : lines) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) result.add(value);
        }
        return result;
    }

    private static List<Case> loadCatalog(Path catalog) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", true);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "file");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // The trusted catalog is assembled from many local external entities. This applies only
        // to catalog loading; both processors under test keep DTD/entity support disabled.
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/totalEntitySizeLimit", "0");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxGeneralEntitySizeLimit", "0");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxParameterEntitySizeLimit", "0");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/entityExpansionLimit", "0");
        Document document = factory.newDocumentBuilder().parse(catalog.toFile());
        NodeList tests = document.getElementsByTagName("TEST");
        List<Case> result = new ArrayList<Case>(tests.getLength());
        Path root = catalog.getParent().toRealPath();
        for (int i = 0; i < tests.getLength(); i++) {
            Element element = (Element) tests.item(i);
            URI resolved = URI.create(element.getBaseURI()).resolve(element.getAttribute("URI"));
            Path path = new File(resolved).toPath().toRealPath();
            if (!path.startsWith(root)) throw new IllegalStateException("Test escapes corpus: " + path);
            result.add(new Case(element.getAttribute("ID"), element.getAttribute("TYPE"),
                    element.getAttribute("RECOMMENDATION"), element.getAttribute("VERSION"),
                    element.getAttribute("EDITION"), element.getAttribute("ENTITIES"),
                    !"no".equals(element.getAttribute("NAMESPACE")), path));
        }
        return Collections.unmodifiableList(result);
    }

    private static String classify(Case test, byte[] bytes) {
        if (test.recommendation.startsWith("XML1.1") || test.recommendation.startsWith("NS1.1")
                || "1.1".equals(test.version)) return "UNSUPPORTED_XML11";
        if (test.recommendation.startsWith("NS1.0")) return "UNSUPPORTED_NAMESPACE_CONFORMANCE";
        if ("invalid".equals(test.type)) return "UNSUPPORTED_DTD_VALIDATION";
        if ("error".equals(test.type)) return "UNSUPPORTED_OPTIONAL_ERROR";
        if (!"none".equals(test.entities) || asciiContains(bytes, "<!DOCTYPE"))
            return "UNSUPPORTED_DTD_OR_EXTERNAL_ENTITY";
        if (!isUtf8OrAscii(bytes)) return "UNSUPPORTED_INPUT_ENCODING";
        if (!"valid".equals(test.type) && !"not-wf".equals(test.type)) return "UNSUPPORTED_CLASSIFICATION";
        return "CORE_XML10_UTF8";
    }

    private static boolean acceptsWithJdkStax(Case test, byte[] bytes) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.valueOf(test.namespaceAware));
        set(factory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(test.path.toUri().toString(),
                    new ByteArrayInputStream(bytes));
            try { while (reader.hasNext()) reader.next(); }
            finally { reader.close(); }
            return true;
        } catch (Exception expected) { return false; }
    }

    private static boolean acceptsWithSimdxml(byte[] bytes) {
        try {
            SimdXmlStreamReader reader = new SimdXmlParser(Math.max(4096, bytes.length + 64), 2048).stream(bytes);
            while (reader.hasNext()) reader.next();
            return true;
        } catch (RuntimeException expected) { return false; }
    }

    private static boolean expectedAccept(String type) { return "valid".equals(type); }
    private static String expected(String type) { return expectedAccept(type) ? "ACCEPT" : "REJECT"; }

    private static boolean reserve(Map<String, Integer> counts, String key, int limit) {
        Integer count = counts.get(key);
        int value = count == null ? 0 : count.intValue();
        if (value >= limit) return false;
        counts.put(key, Integer.valueOf(value + 1));
        return true;
    }

    private static void increment(Map<String, Integer> counts, String key) {
        Integer count = counts.get(key);
        counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }

    private static boolean asciiContains(byte[] bytes, String token) {
        byte[] needle = token.getBytes(StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                int value = bytes[i + j] & 0xff;
                int expected = needle[j] & 0xff;
                if (value >= 'a' && value <= 'z') value -= 32;
                if (expected >= 'a' && expected <= 'z') expected -= 32;
                if (value != expected) continue outer;
            }
            return true;
        }
        return false;
    }

    private static boolean isUtf8OrAscii(byte[] bytes) {
        if (bytes.length >= 2 && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) return false;
        int length = Math.min(bytes.length, 256);
        String head = new String(bytes, 0, length, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
        int encoding = head.indexOf("ENCODING");
        if (encoding < 0) return true;
        int equals = head.indexOf('=', encoding + 8);
        if (equals < 0) return true;
        int quote = equals + 1;
        while (quote < head.length() && Character.isWhitespace(head.charAt(quote))) quote++;
        if (quote >= head.length() || (head.charAt(quote) != '\'' && head.charAt(quote) != '"')) return true;
        int end = head.indexOf(head.charAt(quote), quote + 1);
        if (end < 0) return true;
        String value = head.substring(quote + 1, end);
        return "UTF-8".equals(value) || "UTF8".equals(value) || "US-ASCII".equals(value) || "ASCII".equals(value);
    }

    private static void set(XMLInputFactory factory, String name, Object value) {
        try { factory.setProperty(name, value); }
        catch (IllegalArgumentException unsupported) { /* capability recorded by actual outcome */ }
    }

    private static String tsv(String value) { return value.replace('\t', ' ').replace('\n', ' '); }
    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing -D" + name);
        return value;
    }
    private static String joinFailures(List<String> failures) {
        StringBuilder message = new StringBuilder("Representative W3C gate failures: ");
        for (int i = 0; i < failures.size() && i < 20; i++) message.append('\n').append(failures.get(i));
        if (failures.size() > 20) message.append("\n...").append(failures.size() - 20).append(" more");
        return message.toString();
    }

    private static final class Case {
        final String id, type, recommendation, version, edition, entities;
        final boolean namespaceAware;
        final Path path;
        Case(String id, String type, String recommendation, String version, String edition,
             String entities, boolean namespaceAware, Path path) {
            this.id = id; this.type = type; this.recommendation = recommendation;
            this.version = version; this.edition = edition; this.entities = entities;
            this.namespaceAware = namespaceAware; this.path = path;
        }
    }
}
