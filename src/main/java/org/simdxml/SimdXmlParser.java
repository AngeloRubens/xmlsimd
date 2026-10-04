package org.simdxml;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reusable two-stage UTF-8 XML parser. Instances are intentionally not thread safe.
 * External entities and DTDs are rejected; predefined and numeric entities are supported.
 */
public final class SimdXmlParser extends AbstractXmlParser {
    private static final int DEFAULT_CAPACITY = 34 * 1024 * 1024;
    private static final int DEFAULT_MAX_DEPTH = 1024;
    private final int capacity;
    private final boolean verticalOptimizations;
    private final VerticalProfile verticalProfile;
    private final int tinyDocumentThreshold;
    private final StructuralIndexer indexer = new StructuralIndexer();
    private final StructuralIndex structurals;
    private SimdXmlStreamReader reusableReader;
    private final XmlBinder binder = new XmlBinder();
    private final PaymentXmlInspector paymentInspector = new PaymentXmlInspector(this);
    private final FhirXmlInspector fhirInspector = new FhirXmlInspector(this);
    private final Hl7v2Inspector hl7v2Inspector = new Hl7v2Inspector();
    private final HealthcareFlyweight healthcareFlyweight = new HealthcareFlyweight();

    public static SimdXmlParserBuilder builder() { return new SimdXmlParserBuilder(); }

    public SimdXmlParser() { this(DEFAULT_CAPACITY, DEFAULT_MAX_DEPTH); }
    public SimdXmlParser(int capacity, int maxDepth) {
        this(capacity, maxDepth, VerticalProfile.configured(), configuredUtf8Validation());
    }
    public SimdXmlParser(int capacity, int maxDepth, boolean verticalOptimizations) {
        this(capacity, maxDepth, verticalOptimizations, configuredUtf8Validation());
    }
    public SimdXmlParser(int capacity, int maxDepth, boolean verticalOptimizations, Utf8Validation utf8Validation) {
        this(capacity, maxDepth, verticalOptimizations ? VerticalProfile.AUTO : VerticalProfile.NONE, utf8Validation);
    }
    public SimdXmlParser(int capacity, int maxDepth, VerticalProfile verticalProfile, Utf8Validation utf8Validation) {
        this(capacity, maxDepth, verticalProfile, utf8Validation, -1);
    }
    /** {@code tinyThreshold < 0} keeps {@code -Dorg.simdxml.tiny.threshold}, whose default is 4096. */
    SimdXmlParser(int capacity, int maxDepth, VerticalProfile verticalProfile, Utf8Validation utf8Validation,
            int tinyThreshold) {
        super(maxDepth, utf8Validation);
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.verticalProfile = java.util.Objects.requireNonNull(verticalProfile, "verticalProfile");
        this.verticalOptimizations = verticalProfile != VerticalProfile.NONE;
        this.tinyDocumentThreshold = tinyThreshold >= 0 ? tinyThreshold
                : Math.max(0, Integer.getInteger("org.simdxml.tiny.threshold", 4096));
        this.structurals = new StructuralIndex(capacity);
    }

    /** Active stage-1 backend, for diagnostics and benchmarks. */
    public String indexingStrategy() { return indexer.strategyName(); }
    public boolean verticalOptimizationsEnabled() { return verticalOptimizations; }
    public VerticalProfile verticalProfile() { return verticalProfile; }
    public String utf8ValidationStrategy() { return Utf8Validator.strategyName(); }
    public int tinyDocumentThreshold() { return tinyDocumentThreshold; }
    public String optimizationProfile(boolean reusable) {
        return "tiny=" + tinyDocumentThreshold + ",lazy-bytes,small-attrs,compact-stack,cacheline-index"
                + (reusable ? ",flyweight-reader,persistent-symbols" : "");
    }

    /** Projects SOAP/WS-Addressing/HL7 metadata, using the optional vertical streaming fast path. */
    public HealthcareMessageInfo inspectHealthcare(byte[] input) {
        return HealthcareSoapInspector.inspect(this, input, verticalOptimizations);
    }

    /** Executes a scoped zero-copy SOAP/WS-Addressing/HL7 projection. */
    public <R> R withHealthcareFlyweight(byte[] input, HealthcareFlyweightFunction<R> operation) {
        java.util.Objects.requireNonNull(operation, "operation");
        HealthcareSoapInspector.fill(this, input, verticalOptimizations, healthcareFlyweight);
        return operation.apply(healthcareFlyweight);
    }

    /** Projects ISO 20022/SEPA payment routing and audit fields without constructing a tree. */
    public PaymentMessageInfo inspectPayment(byte[] input) {
        return paymentInspector.inspect(input, verticalOptimizations);
    }

    /** Executes a zero-copy ISO 20022 projection; the supplied view is valid only inside the callback. */
    public <R> R withPaymentFlyweight(byte[] input, PaymentFlyweightFunction<R> operation) {
        java.util.Objects.requireNonNull(operation, "operation");
        return paymentInspector.withFlyweight(input, verticalOptimizations, operation);
    }

    /** Projects HL7 v2.x routing and audit fields without constructing a tree. */
    public Hl7v2MessageInfo inspectHl7v2(byte[] input) {
        return hl7v2Inspector.inspect(input);
    }

    /** Projects FHIR XML routing and audit fields without constructing a tree. */
    public FhirMessageInfo inspectFhir(byte[] input) {
        return fhirInspector.inspect(input, verticalOptimizations);
    }

    /** Executes a scoped FHIR projection; the supplied view is valid only inside the callback. */
    public <R> R withFhirFlyweight(byte[] input, FhirFlyweightFunction<R> operation) {
        java.util.Objects.requireNonNull(operation, "operation");
        return fhirInspector.withFlyweight(input, verticalOptimizations, operation);
    }

    /** Dispatches to a configured family; AUTO performs a bounded byte-only probe. */
    public VerticalMessageInfo inspectVertical(byte[] input) {
        return verticalProfile.inspect(this, input);
    }

    public XmlDocument parse(byte[] input) { return parse(input, input.length); }

    /** Parses and validates the SOAP 1.1/1.2 envelope structure without resolving external resources. */
    public SoapMessage parseSoap(byte[] input) { return SoapValidator.validate(parse(input)); }

    /** Parses and validates a SOAP envelope in the selected byte range. */
    public SoapMessage parseSoap(byte[] input, int length) { return SoapValidator.validate(parse(input, length)); }

    /** Streams XML directly into a JAXB-annotated no-arg class. */
    public <T> T parse(byte[] input, Class<T> type) { return binder.bind(stream(input), type); }

    /** Streams the selected byte range directly into a JAXB-annotated Java type. */
    public <T> T parse(byte[] input, int length, Class<T> type) { return binder.bind(stream(input, length), type); }
    Object parseBound(byte[] input, java.util.Map<XmlExpandedName, Class<?>> roots) {
        return binder.bindAny(stream(input), roots);
    }

    /** Creates a forward-only reader; no DOM is built. */
    public SimdXmlStreamReader stream(byte[] input) { return stream(input, input.length); }

    /** Creates a forward-only reader over the first {@code length} bytes. */
    public SimdXmlStreamReader stream(byte[] input, int length) {
        byte[] document = prepareDocument(input, length);
        return new SimdXmlStreamReader(document, length, maxDepth, structurals);
    }

    /**
     * Resets and returns one parser-owned reader. The previous reader view becomes invalid;
     * intended for sequential high-throughput gateways using one parser per thread.
     */
    public SimdXmlStreamReader reusableStream(byte[] input) { return reusableStream(input, input.length); }

    public SimdXmlStreamReader reusableStream(byte[] input, int length) {
        byte[] document = prepareDocument(input, length);
        if (reusableReader == null) reusableReader = new SimdXmlStreamReader(document, length, maxDepth, structurals, true);
        else reusableReader.reset(document, length);
        return reusableReader;
    }

    /** Allocation-bounded SBE-style callback scan over the parser-owned flyweight reader. */
    public void scanReusable(byte[] input, XmlEventConsumer consumer) {
        java.util.Objects.requireNonNull(consumer, "consumer");
        SimdXmlStreamReader reader = reusableStream(input);
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            consumer.onEvent(event, reader);
        }
    }

    /** Validates, then builds the structural index the reader will consume. */
    private byte[] prepareDocument(byte[] input, int length) {
        if (length < 0 || length > input.length) throw new IndexOutOfBoundsException("Invalid length: " + length);
        if (length > capacity) throw new IllegalArgumentException("Document exceeds parser capacity of " + capacity + " bytes");
        byte[] document = input.length == length ? input : java.util.Arrays.copyOf(input, length);
        if (utf8Validation == Utf8Validation.STRICT) validateUtf8(document, length);
        prepareStructurals(document, length);
        return document;
    }

    public XmlDocument parse(byte[] input, int length) {
        return new Stage2(prepareDocument(input, length), length).parse();
    }

    private void prepareStructurals(byte[] document, int length) {
        if (length <= tinyDocumentThreshold) structurals.useDirectSearch();
        else indexer.index(document, length, structurals);
    }

    private static void validateUtf8(byte[] input, int length) {
        Utf8Validator.validate(input, length);
    }
    private final class Stage2 {
        private final byte[] in;
        private final int end;
        private final Deque<XmlElement> stack = new ArrayDeque<>();
        private final List<XmlNode> prolog = new ArrayList<>();
        private final List<XmlNode> epilog = new ArrayList<>();
        private XmlElement root;
        private int p;

        Stage2(byte[] input, int length) { in = input; end = length; }

        XmlDocument parse() {
            if (startsRaw(0, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) p = 3;
            while (p < end) {
                int markup = structurals.next(p, (byte) '<', in);
                if (markup > end) markup = end;
                if (markup > p) text(p, markup);
                p = markup;
                if (p == end) break;
                byte kind = peek(1);
                if (kind == '?') processingInstruction();
                else if (kind == '/') closeElement();
                else if (kind == '!') {
                    if (starts(p, XmlByteUtils.COMMENT_OPEN)) comment();
                    else if (starts(p, XmlByteUtils.CDATA_OPEN)) cdata();
                    else if (startsIgnoreCase(p, XmlByteUtils.DOCTYPE_OPEN))
                        fail("DTDs and external entities are not supported", p);
                    else fail("Unsupported declaration", p);
                } else openElement();
            }
            if (!stack.isEmpty()) fail("Unclosed element <" + stack.peek().name() + ">", end);
            if (root == null) fail("Document has no root element", end);
            return new XmlDocument(root, prolog, epilog);
        }

        private void openElement() {
            int start = p++;
            String name = name();
            boolean separated = p < end && isSpace(in[p]);
            XmlElement element = new XmlElement(name);
            skipSpace();
            while (p < end && in[p] != '>' && !(in[p] == '/' && peek(1) == '>')) {
                if (!separated) fail("Whitespace required before attribute", p);
                int attrOffset = p;
                String attrName = name();
                skipSpace(); expect('='); skipSpace();
                byte quote = peek(0);
                if (quote != '\'' && quote != '"') fail("Attribute value must be quoted", p);
                p++;
                int valueStart = p;
                while (p < end && in[p] != quote) p++;
                if (p == end) fail("Unclosed attribute value", valueStart);
                try { element.addAttribute(attrName, decode(valueStart, p)); }
                catch (IllegalArgumentException e) { fail(e.getMessage(), attrOffset); }
                p++;
                separated = p < end && isSpace(in[p]);
                skipSpace();
            }
            boolean empty = p < end && in[p] == '/';
            if (empty) p++;
            expect('>');
            if (stack.isEmpty()) {
                if (root != null) fail("Multiple root elements", start);
                root = element;
            } else stack.peek().addChild(element);
            if (!empty) {
                if (stack.size() >= maxDepth) fail("Maximum depth of " + maxDepth + " exceeded", start);
                stack.push(element);
            }
        }

        private void closeElement() {
            int start = p;
            p += 2; String name = name(); skipSpace(); expect('>');
            if (stack.isEmpty()) fail("Unexpected closing tag </" + name + ">", start);
            String expected = stack.pop().name();
            if (!expected.equals(name)) fail("Expected </" + expected + "> but found </" + name + ">", start);
        }

        private void text(int from, int to) {
            String value = decode(from, to);
            if (stack.isEmpty()) {
                if (!value.trim().isEmpty()) fail("Character data outside root element", from);
            } else if (!value.isEmpty()) stack.peek().addChild(new XmlText(value, false));
        }

        private void comment() {
            int start = p + 4;
            int close = find(start, XmlByteUtils.COMMENT_CLOSE);
            if (close < 0) fail("Unclosed comment", p);
            String value = raw(start, close);
            if (value.contains("--")) fail("'--' is not allowed inside comments", start);
            addMisc(new XmlComment(value)); p = close + 3;
        }

        private void cdata() {
            if (stack.isEmpty()) fail("CDATA outside root element", p);
            int start = p + 9;
            int close = find(start, XmlByteUtils.CDATA_CLOSE);
            if (close < 0) fail("Unclosed CDATA section", p);
            stack.peek().addChild(new XmlText(raw(start, close), true)); p = close + 3;
        }

        private void processingInstruction() {
            int offset = p;
            p += 2; String target = name();
            int dataStart = p; int close = find(p, XmlByteUtils.PI_CLOSE);
            if (close < 0) fail("Unclosed processing instruction", offset);
            String data = raw(dataStart, close).trim(); p = close + 2;
            if (target.equalsIgnoreCase("xml")) {
                if (offset != 0 && offset != 3) fail("XML declaration must be first", offset);
                if (!data.matches("version\\s*=\\s*(['\"])1\\.[01]\\1(?:\\s+encoding\\s*=\\s*(['\"])UTF-8\\2)?(?:\\s+standalone\\s*=\\s*(['\"])(?:yes|no)\\3)?\\s*"))
                    fail("Unsupported or malformed XML declaration", offset);
                return;
            }
            addMisc(new XmlProcessingInstruction(target, data));
        }

        private void addMisc(XmlNode node) {
            if (!stack.isEmpty()) stack.peek().addChild(node);
            else if (root == null) prolog.add(node);
            else epilog.add(node);
        }

        private String name() {
            int start = p;
            if (p >= end || !nameStart(in[p])) fail("Expected XML name", p);
            p++;
            while (p < end && namePart(in[p])) p++;
            return raw(start, p);
        }
        private boolean nameStart(byte b) { return b == ':' || b == '_' || asciiLetter(b) || b < 0; }
        private boolean namePart(byte b) { return nameStart(b) || b == '-' || b == '.' || (b >= '0' && b <= '9'); }
        private boolean asciiLetter(byte b) { return (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z'); }
        private boolean isSpace(byte b) { return b == ' ' || b == '\t' || b == '\n' || b == '\r'; }
        private void skipSpace() { while (p < end && isSpace(in[p])) p++; }
        private void expect(char c) { if (p >= end || in[p] != (byte)c) fail("Expected '" + c + "'", p); p++; }
        private byte peek(int delta) { return p + delta < end ? in[p + delta] : 0; }
        private boolean startsRaw(int at, byte... bytes) {
            if (at + bytes.length > end) return false;
            for (int i = 0; i < bytes.length; i++) if (in[at + i] != bytes[i]) return false;
            return true;
        }
        private boolean starts(int at, byte[] value) { return XmlByteUtils.starts(in, end, at, value); }
        private boolean startsIgnoreCase(int at, byte[] value) { return XmlByteUtils.startsIgnoreAsciiCase(in, end, at, value); }
        private int find(int from, byte[] needle) { return XmlByteUtils.find(in, end, from, needle); }
        private String raw(int from, int to) { return new String(in, from, to - from, StandardCharsets.UTF_8); }
        private String decode(int from, int to) {
            int amp = structurals.next(from, to, (byte)'&', in);
            if (amp >= to) return raw(from, to);
            StringBuilder out = new StringBuilder(to - from);
            int cursor = from;
            while (amp < to) {
                out.append(raw(cursor, amp));
                int semi = amp + 1;
                while (semi < to && in[semi] != ';') semi++;
                if (semi == to) fail("Unclosed entity reference", amp);
                String entity = raw(amp + 1, semi);
                if ("lt".equals(entity)) out.append('<'); else if ("gt".equals(entity)) out.append('>');
                else if ("amp".equals(entity)) out.append('&'); else if ("apos".equals(entity)) out.append('\'');
                else if ("quot".equals(entity)) out.append('"'); else out.append(numericEntity(entity, amp));
                cursor = semi + 1; amp = structurals.next(cursor, to, (byte)'&', in);
            }
            return out.append(raw(cursor, to)).toString();
        }
        private String numericEntity(String entity, int offset) {
            if (!entity.startsWith("#")) { fail("Unknown entity '&" + entity + ";'", offset); return ""; }
            try {
                int radix = entity.startsWith("#x") || entity.startsWith("#X") ? 16 : 10;
                int skip = radix == 16 ? 2 : 1;
                int cp = Integer.parseInt(entity.substring(skip), radix);
                if (!validXmlCodePoint(cp)) fail("Invalid XML character reference", offset);
                return new String(Character.toChars(cp));
            } catch (NumberFormatException e) { fail("Malformed character reference", offset); return ""; }
        }
        private boolean validXmlCodePoint(int cp) {
            return cp == 0x9 || cp == 0xA || cp == 0xD || cp >= 0x20 && cp <= 0xD7FF
                    || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF;
        }
        private void fail(String message, int offset) { throw new XmlParsingException(message, offset); }
    }
}
