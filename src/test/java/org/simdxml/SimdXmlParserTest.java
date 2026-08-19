package org.simdxml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.lang.foreign.Arena;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SimdXmlParserTest {
    private final SimdXmlParser parser = new SimdXmlParser(64 * 1024, 32);

    @Test void parsesElementsAttributesEntitiesAndMixedContent() {
        XmlDocument document = parse("<?xml version=\"1.0\"?><catalog><!--x--><book id='7'>A &amp; "
                + "<![CDATA[<B>]]><price currency=\"EUR\">12</price></book></catalog>");
        XmlElement book = document.root().child("book").orElseThrow();
        assertEquals("7", book.attribute("id").orElseThrow());
        assertEquals("A & <B>12", book.text());
        assertEquals("EUR", book.child("price").orElseThrow().attribute("currency").orElseThrow());
    }

    @Test void retainsPrologAndEpilogMiscNodes() {
        XmlDocument document = parse("<!--before--><?work yes?><root/><!--after-->");
        assertEquals(2, document.prolog().size());
        assertEquals(1, document.epilog().size());
    }

    @Test void supportsUnicodeAndNumericReferences() {
        assertEquals("caffè 😀", parse("<x>caffè &#x1F600;</x>").root().text());
    }

    @Test void rejectsMalformedAndDangerousDocuments() {
        assertThrows(XmlParsingException.class, () -> parse("<a><b></a>"));
        assertThrows(XmlParsingException.class, () -> parse("<a x='1'x='2'/>"));
        assertThrows(XmlParsingException.class, () -> parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"));
        assertThrows(XmlParsingException.class, () -> parse("<a>&unknown;</a>"));
    }

    @Test void rejectsInvalidUtf8() {
        assertThrows(XmlParsingException.class, () -> parser.parse(new byte[]{'<','x','>',(byte)0xC3,0x28,'<','/','x','>'}));
    }

    @Test void utf8ValidationCanBeDisabledExplicitlyForPrevalidatedInput() {
        SimdXmlParser trustedInputParser = new SimdXmlParser(1024, 16, true, Utf8Validation.NONE);
        assertEquals(Utf8Validation.NONE, trustedInputParser.utf8Validation());
        assertEquals("ok", trustedInputParser.parse("<x>ok</x>".getBytes(StandardCharsets.UTF_8)).root().text());
        assertDoesNotThrow(() -> trustedInputParser.parse(
                new byte[]{'<','x','>',(byte) 0xc3,0x28,'<','/','x','>'}));
    }

    @Test void swarUtf8ValidatorHandlesBoundariesAndRejectsNonCanonicalSequences() {
        assertEquals("¢€😀", parse("<x>¢€😀</x>").root().text());
        byte[][] invalid = {
                {(byte)0xC0, (byte)0x80},                         // overlong ASCII
                {(byte)0xE0, (byte)0x80, (byte)0x80},             // overlong 3-byte
                {(byte)0xED, (byte)0xA0, (byte)0x80},             // UTF-16 surrogate
                {(byte)0xF4, (byte)0x90, (byte)0x80, (byte)0x80}, // above U+10FFFF
                {(byte)0xF0, (byte)0x9F, (byte)0x98}              // truncated
        };
        for (byte[] bytes : invalid)
            assertThrows(XmlParsingException.class, () -> parser.parse(bytes));
    }

    @Test void swarDelimiterSearchWorksAtAllWordAlignments() {
        for (int lane = 0; lane < 16; lane++) {
            String pad = "x".repeat(lane);
            XmlDocument document = parse("<r>" + pad + "<!--comment--><![CDATA[data]]><?pi value?></r>");
            assertEquals(pad + "data", document.root().text(), "delimiter lane " + lane);
        }
    }

    @Test void validatesRealisticIheHl7HealthcareSoap12() throws IOException {
        byte[] message = getClass().getResourceAsStream("/healthcare/ihe-xcpd-soap12.xml").readAllBytes();
        SoapMessage soap = parser.parseSoap(message);
        assertEquals(SoapVersion.SOAP_1_2, soap.version());
        assertTrue(soap.optionalHeader().isPresent());
        assertEquals("hl7:PRPA_IN201305UV02", soap.body().childElements().getFirst().name());
    }

    @Test void inspectsStandardSoap11WithoutClassifyingItAsHealthcare() throws IOException {
        byte[] message = getClass().getResourceAsStream("/soap/standard-soap11.xml").readAllBytes();
        HealthcareMessageInfo info = parser.inspectHealthcare(message);
        assertEquals(SoapVersion.SOAP_1_1, info.soapVersion());
        assertEquals(HealthcareProtocol.SOAP, info.protocol());
        assertEquals("ord:GetOrderRequest", info.payloadName());
        assertEquals("urn:example:orders:GetOrder", info.action());
        parser.withHealthcareFlyweight(message, view -> {
            assertEquals(HealthcareProtocol.SOAP, view.protocol());
            assertTrue(view.action().isZeroCopy());
            assertEquals("urn:example:orders:GetOrder", view.action().value());
            return null;
        });
    }

    @Test void healthcareVerticalFastPathCanBeDisabledWithEquivalentProjection() throws IOException {
        byte[] message = getClass().getResourceAsStream("/healthcare/ihe-xcpd-soap12.xml").readAllBytes();
        HealthcareMessageInfo fast = new SimdXmlParser(message.length, 64, true).inspectHealthcare(message);
        HealthcareMessageInfo generic = new SimdXmlParser(message.length, 64, false).inspectHealthcare(message);
        assertEquals(generic, fast);
        assertEquals(HealthcareProtocol.HL7_V3, fast.protocol());
        assertEquals("urn:ihe:iti:2009:CrossGatewayPatientDiscovery", fast.action());
        assertEquals("urn:uuid:11111111-2222-3333-4444-555555555555", fast.messageId());
        assertEquals("hl7:PRPA_IN201305UV02", fast.payloadName());
    }

    @Test void rejectsInvalidSoapStructureAndSoapWithDoctype() {
        assertThrows(SoapValidationException.class,
                () -> parser.parseSoap("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'><s:Header/></s:Envelope>"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThrows(XmlParsingException.class,
                () -> parser.parseSoap("<!DOCTYPE x [<!ENTITY p SYSTEM 'file:///etc/passwd'>]><x>&p;</x>"
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test void streamsWithoutBuildingADom() {
        SimdXmlStreamReader reader = parser.stream("<r id='1'>hello<x/>world</r>".getBytes(StandardCharsets.UTF_8));
        List<XmlEvent> events = new ArrayList<>();
        while (reader.hasNext()) events.add(reader.next());
        assertEquals(List.of(XmlEvent.START_DOCUMENT, XmlEvent.START_ELEMENT, XmlEvent.TEXT,
                XmlEvent.START_ELEMENT, XmlEvent.END_ELEMENT, XmlEvent.TEXT,
                XmlEvent.END_ELEMENT, XmlEvent.END_DOCUMENT), events);
    }

    @Test void exposesRootNamespaceAttributesWithLazyNames() {
        SimdXmlStreamReader reader = parser.stream("<soap:Envelope xmlns:soap='urn:test'/ >".replace("/ >", "/>" ).getBytes(StandardCharsets.UTF_8));
        assertEquals(XmlEvent.START_DOCUMENT, reader.next());
        assertEquals(XmlEvent.START_ELEMENT, reader.next());
        assertEquals("soap:Envelope", reader.name());
        assertEquals("urn:test", reader.attributes().get("xmlns:soap"), reader.attributes().toString());
    }

    @Test void reusableReaderResetsAcrossDifferentBuffersAndRetainsCorrectSymbols() {
        SimdXmlParser reusable = new SimdXmlParser(4096, 32);
        SimdXmlStreamReader first = reusable.reusableStream("<a><same x='1'>one</same></a>".getBytes(StandardCharsets.UTF_8));
        while (first.hasNext()) first.next();
        SimdXmlStreamReader second = reusable.reusableStream(
                "<longerRoot><same x='2'>two</same></longerRoot>".getBytes(StandardCharsets.UTF_8));
        List<String> names = new ArrayList<>();
        while (second.hasNext()) {
            XmlEvent event = second.next();
            if (event == XmlEvent.START_ELEMENT) names.add(second.name());
        }
        assertEquals(List.of("longerRoot", "same"), names);
    }

    @Test void tinySinglePassAndIndexedTwoStageProduceEquivalentTrees() {
        byte[] xml = "<r a='1'>text&amp;more<x><![CDATA[data]]></x></r>".getBytes(StandardCharsets.UTF_8);
        String previous = System.setProperty("org.simdxml.tiny.threshold", "0");
        try {
            SimdXmlParser indexed = new SimdXmlParser(4096, 32);
            assertEquals(parser.parse(xml).root().text(), indexed.parse(xml).root().text());
        } finally {
            if (previous == null) System.clearProperty("org.simdxml.tiny.threshold");
            else System.setProperty("org.simdxml.tiny.threshold", previous);
        }
    }

    @Test void exposesSbeStyleByteFlyweightsAndReusableCallbackScan() {
        SimdXmlParser flyweight = new SimdXmlParser(1024, 16);
        List<String> seen = new ArrayList<>();
        flyweight.scanReusable("<root><item>value</item></root>".getBytes(StandardCharsets.UTF_8), (event, reader) -> {
            if (event == XmlEvent.START_ELEMENT) seen.add(reader.nameBytes().decodeUtf8());
            if (event == XmlEvent.TEXT) seen.add(reader.rawTextBytes().decodeUtf8());
        });
        assertEquals(List.of("root", "item", "value"), seen);
    }

    @Test void builderThreadSafeStrategyUsesIndependentReusableContexts() throws Exception {
        ThreadSafeSimdXmlParser shared = SimdXmlParser.builder()
                .withCapacity(4096).withMaxDepth(32).buildThreadSafe();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Callable<Boolean>> jobs = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                int id = thread;
                jobs.add(() -> {
                    String expected = "v" + id;
                    byte[] xml = ("<root><value>" + expected + "</value></root>").getBytes(StandardCharsets.UTF_8);
                    String result = null;
                    for (int iteration = 0; iteration < 500; iteration++) {
                        SimdXmlStreamReader reader = shared.reusableStream(xml);
                        while (reader.hasNext()) {
                            XmlEvent event = reader.next();
                            if (event == XmlEvent.TEXT) result = reader.text();
                        }
                    }
                    shared.removeThreadLocalState();
                    return expected.equals(result);
                });
            }
            for (var future : executor.invokeAll(jobs)) assertTrue(future.get());
        }
    }

    @Test void builderThreadSafeStrategySupportsVirtualThreadsWithPooledContexts() throws Exception {
        ThreadSafeSimdXmlParser shared = SimdXmlParser.builder()
                .withCapacity(4096).withMaxDepth(32).buildThreadSafe();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Callable<Boolean>> jobs = new ArrayList<>();
            for (int task = 0; task < 500; task++) {
                int id = task;
                jobs.add(() -> {
                    String value = "virtual-" + id;
                    XmlDocument document = shared.parse(("<root>" + value + "</root>").getBytes(StandardCharsets.UTF_8));
                    return value.equals(document.root().text());
                });
            }
            for (var future : executor.invokeAll(jobs)) assertTrue(future.get());
        }
    }

    @Test void scopedVerticalFlyweightsAreIsolatedAcrossVirtualThreads() throws Exception {
        final byte[] payment = getClass().getResourceAsStream("/payments/pain.001.001.09.xml").readAllBytes();
        final byte[] soap = getClass().getResourceAsStream("/soap/standard-soap11.xml").readAllBytes();
        ThreadSafeSimdXmlParser shared = SimdXmlParser.builder().withCapacity(4096).withMaxDepth(64).buildThreadSafe();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Callable<Boolean>> jobs = new ArrayList<>();
            for (int task = 0; task < 250; task++) {
                final boolean usePayment = (task & 1) == 0;
                jobs.add(() -> usePayment
                        ? shared.withPaymentFlyweight(payment, value -> value.messageId().isZeroCopy()
                                && "SIMDXML-20260819-001".equals(value.messageId().value()))
                        : shared.withHealthcareFlyweight(soap, value -> value.action().isZeroCopy()
                                && "urn:example:orders:GetOrder".equals(value.action().value())));
            }
            for (var future : executor.invokeAll(jobs)) assertTrue(future.get());
        }
    }

    @Test void directBackendScansDirectByteBufferAndNativeMemoryWithoutHeapCopy() {
        byte[] xml = "<root a='1'><item>offheap</item><![CDATA[data]]></root>".getBytes(StandardCharsets.UTF_8);
        DirectSimdXmlParser direct = new DirectSimdXmlParser(32, Utf8Validation.STRICT);
        List<String> directEvents = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocateDirect(xml.length).put(xml).flip();
        direct.scan(buffer, (event, name, text) -> {
            if (name != null) directEvents.add(event + ":" + name.decodeUtf8());
            else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) directEvents.add(event + ":" + text.decodeUtf8());
        });
        assertEquals(List.of("START_ELEMENT:root", "START_ELEMENT:item", "TEXT:offheap",
                "END_ELEMENT:item", "CDATA:data", "END_ELEMENT:root"), directEvents);

        try (Arena arena = Arena.ofConfined()) {
            var nativeXml = arena.allocate(xml.length);
            nativeXml.copyFrom(java.lang.foreign.MemorySegment.ofArray(xml));
            List<String> names = new ArrayList<>();
            direct.scan(nativeXml, (event, name, text) -> {
                if (event == XmlEvent.START_ELEMENT) names.add(name.decodeUtf8());
            });
            assertEquals(List.of("root", "item"), names);
        }
    }

    @Test void directBackendRejectsMalformedUtf8AndMismatchedTags() {
        DirectSimdXmlParser direct = new DirectSimdXmlParser(16, Utf8Validation.STRICT);
        assertThrows(XmlParsingException.class, () -> direct.scan(
                ByteBuffer.wrap(new byte[]{'<','x','>',(byte)0xc3,0x28,'<','/','x','>'}), (e, n, t) -> { }));
        assertThrows(XmlParsingException.class, () -> direct.scan(
                ByteBuffer.wrap("<a><b></a>".getBytes(StandardCharsets.UTF_8)), (e, n, t) -> { }));
    }

    @Test void directBackendExposesOffHeapFlyweightAttributes() {
        byte[] xml = "<root id='42' code='ABC'/>".getBytes(StandardCharsets.UTF_8);
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(xml.length).put(xml).flip();
        DirectSimdXmlParser direct = new DirectSimdXmlParser(16, Utf8Validation.STRICT);
        List<String> values = new ArrayList<>();
        direct.scan(directBuffer, (DirectXmlEventConsumerEx) (event, name, text, attributes) -> {
            if (event == XmlEvent.START_ELEMENT) {
                assertEquals(2, attributes.size());
                values.add(attributes.rawValueAscii("id".getBytes(StandardCharsets.US_ASCII)).decodeUtf8());
                values.add(attributes.rawValueAscii("code".getBytes(StandardCharsets.US_ASCII)).decodeUtf8());
            }
        });
        assertEquals(List.of("42", "ABC"), values);
    }

    @Test void directBackendProjectsStandardSoapAndHl7FromOffHeapMemory() throws IOException {
        DirectSimdXmlParser direct = new DirectSimdXmlParser(128, Utf8Validation.STRICT);
        byte[] soap = getClass().getResourceAsStream("/soap/standard-soap11.xml").readAllBytes();
        byte[] hl7 = getClass().getResourceAsStream("/healthcare/ihe-xcpd-soap12.xml").readAllBytes();
        ByteBuffer soapBuffer = ByteBuffer.allocateDirect(soap.length).put(soap).flip();
        ByteBuffer hl7Buffer = ByteBuffer.allocateDirect(hl7.length).put(hl7).flip();
        HealthcareMessageInfo soapInfo = direct.inspectHealthcare(soapBuffer);
        HealthcareMessageInfo hl7Info = direct.inspectHealthcare(hl7Buffer);
        assertEquals(HealthcareProtocol.SOAP, soapInfo.protocol());
        assertEquals("urn:example:orders:GetOrder", soapInfo.action());
        assertEquals(HealthcareProtocol.HL7_V3, hl7Info.protocol());
        assertEquals("hl7:PRPA_IN201305UV02", hl7Info.payloadName());
    }

    @Test void paymentVerticalProjectsIso20022BytesAndMatchesGenericPath() throws IOException {
        byte[] payment = getClass().getResourceAsStream("/payments/pain.001.001.09.xml").readAllBytes();
        PaymentMessageInfo fast = new SimdXmlParser(payment.length, 64, true).inspectPayment(payment);
        PaymentMessageInfo generic = new SimdXmlParser(payment.length, 64, false).inspectPayment(payment);
        assertEquals(generic, fast);
        assertEquals(PaymentProtocol.SEPA, fast.protocol());
        assertEquals("CstmrCdtTrfInitn", fast.messageType());
        assertEquals("SIMDXML-20260819-001", fast.messageId());
        assertEquals(2, fast.transactionCount());
        assertEquals("1250.75", fast.controlSum());
        assertEquals("IT60X0542811101000000123456", fast.firstIban());
        assertEquals("BPPIITRRXXX", fast.firstBic());
        SimdXmlParser fixed = SimdXmlParser.builder().withCapacity(payment.length).withMaxDepth(64)
                .withVerticalProfile(VerticalProfile.PAYMENTS).build();
        SimdXmlParser automatic = SimdXmlParser.builder().withCapacity(payment.length).withMaxDepth(64)
                .withVerticalProfile(VerticalProfile.AUTO).build();
        assertEquals(fast, fixed.inspectVertical(payment));
        assertEquals(fast, automatic.inspectVertical(payment));
        assertEquals(VerticalProfile.PAYMENTS, fixed.verticalProfile());
        fixed.withPaymentFlyweight(payment, view -> {
            assertEquals(PaymentProtocol.SEPA, view.protocol());
            assertEquals(2, view.transactionCount());
            assertTrue(view.messageId().isZeroCopy());
            assertTrue(view.controlSum().isZeroCopy());
            assertTrue(view.firstIban().isZeroCopy());
            assertEquals("SIMDXML-20260819-001", view.messageId().value());
            assertEquals("IT60X0542811101000000123456", view.firstIban().value());
            return null;
        });
    }

    @Test void directThreadSafeFacadeSupportsVirtualThreadsAndHealthcareProjection() throws Exception {
        ThreadSafeDirectSimdXmlParser shared = DirectSimdXmlParser.builder()
                .withMaxDepth(128).withUtf8Validation(Utf8Validation.STRICT).buildThreadSafe();
        byte[] soap = getClass().getResourceAsStream("/soap/standard-soap11.xml").readAllBytes();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Callable<Boolean>> jobs = new ArrayList<>();
            for (int task = 0; task < 250; task++) {
                jobs.add(() -> {
                    ByteBuffer input = ByteBuffer.allocateDirect(soap.length).put(soap).flip();
                    HealthcareMessageInfo info = shared.inspectHealthcare(input);
                    return info.protocol() == HealthcareProtocol.SOAP
                            && "urn:example:orders:GetOrder".equals(info.action());
                });
            }
            for (var future : executor.invokeAll(jobs)) assertTrue(future.get());
        }
    }

    @Test void bindsDirectlyToJaxbAnnotatedBeans() {
        Catalog catalog = parser.parse("<catalog><book id='7'>SIMD</book><book id='8'>XML</book></catalog>"
                .getBytes(StandardCharsets.UTF_8), Catalog.class);
        assertEquals(List.of(new Book(7, "SIMD"), new Book(8, "XML")), catalog.books());
    }

    @Test void optimizedJaxbStyleContextPrewarmsAndReusesUnmarshaller() {
        SimdJaxbContext context = SimdJaxbContext.builder(Catalog.class)
                .withCapacity(4096).withMaxDepth(32).build();
        SimdUnmarshaller unmarshaller = context.createUnmarshaller();
        byte[] first = "<catalog><book id='7'>SIMD</book></catalog>".getBytes(StandardCharsets.UTF_8);
        byte[] second = "<catalog><book id='8'>XML</book></catalog>".getBytes(StandardCharsets.UTF_8);
        assertEquals("SIMD", unmarshaller.unmarshal(first, Catalog.class).books().get(0).title());
        assertEquals(8, unmarshaller.unmarshal(second, Catalog.class).books().get(0).id());
        assertEquals("SIMD", context.unmarshal(first, Catalog.class).books().get(0).title());
        assertThrows(XmlBindingException.class, () -> context.unmarshal(first, Book.class));
    }

    @Test void scalarBackendProducesTheSameResult() {
        String previous = System.setProperty("org.simdxml.indexer", "scalar");
        try {
            SimdXmlParser scalar = new SimdXmlParser(1024, 16);
            assertEquals("scalar-swar64", scalar.indexingStrategy());
            assertEquals("a&b", scalar.parse("<x>a&amp;b</x>".getBytes(StandardCharsets.UTF_8)).root().text());
        } finally {
            if (previous == null) System.clearProperty("org.simdxml.indexer");
            else System.setProperty("org.simdxml.indexer", previous);
        }
    }

    @Test void scalarBackendFindsEveryStructuralAtEverySwarLane() {
        String previous = System.setProperty("org.simdxml.indexer", "scalar");
        try {
            for (int lane = 0; lane < 32; lane++) {
                String padding = " ".repeat(lane);
                SimdXmlParser scalar = new SimdXmlParser(2048, 16);
                XmlDocument parsed = scalar.parse((padding + "<root>ok</root>").getBytes(StandardCharsets.UTF_8));
                assertEquals("ok", parsed.root().text(), "SWAR lane " + lane);
            }
        } finally {
            if (previous == null) System.clearProperty("org.simdxml.indexer");
            else System.setProperty("org.simdxml.indexer", previous);
        }
    }

    @Test void optimizedIndexersMatchByteBaselineOnRandomAndAdversarialInputs() {
        IndexingStrategy baseline = new BaselineStructuralIndexer();
        List<IndexingStrategy> optimized = new ArrayList<>();
        optimized.add(new ScalarStructuralIndexer());
        optimized.add(new UnsafeStructuralIndexer());
        try { optimized.add((IndexingStrategy) Class.forName("org.simdxml.VectorStructuralIndexer")
                .getDeclaredConstructor().newInstance()); }
        catch (ReflectiveOperationException | LinkageError vectorUnavailable) { /* no Vector runtime profile */ }
        Random random = new Random(0x1B_CAFE);
        for (int length = 0; length <= 521; length++) {
            byte[] input = new byte[length];
            random.nextBytes(input);
            if (length > 24) {
                byte[] adversarial = {'<', '=', '>', '?', '!', '/', '&', '\'', '"', 0, 1, '<', 1, '>', 1, '&'};
                System.arraycopy(adversarial, 0, input, length / 3, adversarial.length);
            }
            StructuralIndex expected = new StructuralIndex(length);
            baseline.index(input, length, expected);
            for (IndexingStrategy strategy : optimized) {
                StructuralIndex actual = new StructuralIndex(length);
                strategy.index(input, length, actual);
                assertTrue(expected.sameValues(actual), strategy.name() + " differs at length " + length);
            }
        }
    }

    @XmlRootElement(name = "catalog")
    static final class Catalog {
        @jakarta.xml.bind.annotation.XmlElement(name = "book") List<Book> books;
        public Catalog() { }
        Catalog(List<Book> books) { this.books = books; }
        List<Book> books() { return books; }
    }

    static final class Book {
        @XmlAttribute int id; @XmlValue String title;
        public Book() { }
        Book(int id, String title) { this.id = id; this.title = title; }
        int id() { return id; } String title() { return title; }
        @Override public boolean equals(Object other) { return other instanceof Book && id == ((Book) other).id && java.util.Objects.equals(title, ((Book) other).title); }
        @Override public int hashCode() { return 31 * id + java.util.Objects.hashCode(title); }
    }

    private XmlDocument parse(String xml) { return parser.parse(xml.getBytes(StandardCharsets.UTF_8)); }
}
