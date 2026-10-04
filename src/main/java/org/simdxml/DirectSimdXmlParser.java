package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Allocation-bounded, zero-copy XML event scanner for heap/off-heap MemorySegment and ByteBuffer.
 *
 * <p><b>Object binding on this path is namespace-free.</b> {@link #bind} matches elements and
 * attributes by local name only and rejects a document that carries a prefix or an {@code xmlns}
 * declaration, throwing {@link XmlBindingException}. It also does not apply {@code XmlAdapter}s or
 * {@code @XmlElementWrapper} wrappers. Documents with namespaces, wrappers, adapters or otherwise
 * complex JAXB models belong on the standard binder, reached through {@code SimdJaxbContext} and
 * {@code SimdUnmarshaller}; scanning ({@link #scan}) has no such restriction and handles any
 * well-formed document.
 */
public final class DirectSimdXmlParser extends AbstractXmlParser {
    public static DirectSimdXmlParserBuilder builder() { return new DirectSimdXmlParserBuilder(); }
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final long HIGH_BITS = 0x8080808080808080L;
    private final DirectByteFinder scalarFinder = new ScalarDirectByteFinder();
    private final DirectByteFinder largeFinder = selectFinder();
    private final boolean adaptiveFinder = System.getProperty("org.simdxml.direct.strategy", "auto").equals("auto");
    private final long indexThreshold = Math.max(0L, Long.getLong("org.simdxml.direct.index.threshold", Long.MAX_VALUE));
    private DirectByteFinder activeFinder = largeFinder;
    private long[] starts = new long[16], ends = new long[16];
    private int[] hashes = new int[16];
    private long[] attrStarts = new long[8], attrEnds = new long[8], attrValueStarts = new long[8], attrValueEnds = new long[8];
    private long[] attrLocalStarts = new long[8];
    private boolean[] attrValueEntities = new boolean[8];
    private int[] attrHashes = new int[8];
    private long[] markupPositions = new long[4096], entityPositions = new long[1024];
    private int markupSize, entitySize, markupCursor, entityCursor;
    private boolean indexed;
    private final DirectXmlByteSlice name = new DirectXmlByteSlice();
    private final DirectXmlByteSlice text = new DirectXmlByteSlice();
    private final DirectXmlAttributes attributes = new DirectXmlAttributes();
    private DirectXmlBeanBinder beanBinder;
    private DirectXmlEventConsumer consumer;
    private DirectXmlEventConsumerEx extendedConsumer;
    private final DirectHealthcareSoapInspector healthcareInspector = new DirectHealthcareSoapInspector(this);
    private MemorySegment in;
    private long end, p;
    private int depth;
    private boolean rootSeen;

    public DirectSimdXmlParser() { this(1024, Utf8Validation.STRICT); }
    public DirectSimdXmlParser(int maxDepth, Utf8Validation utf8Validation) {
        super(maxDepth, utf8Validation);
    }
    public String memoryStrategy() {
        String access = DirectUnsafeAccess.UNSAFE == null ? "safe" : "unsafe-native";
        return access + "/" + (adaptiveFinder ? "adaptive-swar<=4096/" + largeFinder.name() : largeFinder.name());
    }
    public HealthcareMessageInfo inspectHealthcare(MemorySegment input) { return healthcareInspector.inspect(input); }
    public HealthcareMessageInfo inspectHealthcare(ByteBuffer input) { return healthcareInspector.inspect(MemorySegment.ofBuffer(input.slice())); }
    /** Direct FFM/Vector binding path; values remain in DirectXmlByteSlice until bean conversion. */
    public <T> T bind(MemorySegment input, Class<T> type) {
        // The parser already owns request-local mutable state, so the binder is pooled with it.
        DirectXmlBeanBinder binder = beanBinder;
        if (binder == null) beanBinder = binder = new DirectXmlBeanBinder(type);
        else binder.reset(type);
        scan(input, binder);
        return type.cast(binder.result());
    }
    public <T> T bind(ByteBuffer input, Class<T> type) {
        return bind(MemorySegment.ofBuffer(input.slice()), type);
    }

    public void scan(ByteBuffer buffer, DirectXmlEventConsumer consumer) {
        scan(MemorySegment.ofBuffer(buffer.slice()), consumer);
    }

    public void scan(ByteBuffer buffer, DirectXmlEventConsumerEx consumer) {
        scan(MemorySegment.ofBuffer(buffer.slice()), consumer);
    }

    public void scan(MemorySegment segment, DirectXmlEventConsumer consumer) {
        this.consumer = java.util.Objects.requireNonNull(consumer, "consumer"); this.extendedConsumer = null;
        scanInternal(segment);
    }

    public void scan(MemorySegment segment, DirectXmlEventConsumerEx consumer) {
        this.extendedConsumer = java.util.Objects.requireNonNull(consumer, "consumer"); this.consumer = null;
        scanInternal(segment);
    }

    private void scanInternal(MemorySegment segment) {
        in = java.util.Objects.requireNonNull(segment, "segment");
        end = segment.byteSize(); p = 0; depth = 0; rootSeen = false;
        activeFinder = adaptiveFinder && end <= 4096 ? scalarFinder : largeFinder;
        if (utf8Validation == Utf8Validation.STRICT) validateUtf8();
        indexed = end > indexThreshold;
        if (indexed) buildStructuralIndex();
        if (starts(0, new byte[]{(byte)0xef, (byte)0xbb, (byte)0xbf})) p = 3;
        emit(XmlEvent.START_DOCUMENT, null, null, 0);
        while (p < end) {
            if (b(p) != '<') {
                long from = p;
                p = findByte(p, end, (byte) '<');
                boolean entities = validateEntities(from, p);
                if (depth == 0) {
                    if (!onlySpace(from, p)) fail("Character data outside root element", from);
                } else if (p > from) emit(XmlEvent.TEXT, null, text.reset(in, from, p, from, entities), 0);
                continue;
            }
            byte kind = peek(1);
            if (kind == '?') { processingInstruction(); continue; }
            if (kind == '/') { close(); continue; }
            if (kind == '!') {
                if (starts(p, XmlByteUtils.COMMENT_OPEN)) { comment(); continue; }
                if (starts(p, XmlByteUtils.CDATA_OPEN)) { cdata(); continue; }
                if (startsIgnoreCase(p, XmlByteUtils.DOCTYPE_OPEN)) fail("DTDs and external entities are not supported", p);
                fail("Unsupported declaration", p);
            }
            open();
        }
        if (depth != 0) fail("Unclosed element", end);
        if (!rootSeen) fail("Document has no root element", end);
        emit(XmlEvent.END_DOCUMENT, null, null, 0);
    }

    private void open() {
        long markup = p++;
        long ns = p; int hash = scanName(); long ne = p; long nl = lastLocalStart;
        boolean separated = p < end && space(b(p));
        skipSpace(); int attrCount = 0;
        while (p < end && b(p) != '>' && !(b(p) == '/' && peek(1) == '>')) {
            if (!separated) fail("Whitespace required before attribute", p);
            long as = p; int ah = scanName(); long ae = p; long al = lastLocalStart;
            for (int i = 0; i < attrCount; i++)
                if (attrHashes[i] == ah && ae - as == attrEnds[i] - attrStarts[i] && same(as, attrStarts[i], ae - as))
                    fail("Duplicate attribute", as);
            ensureAttrs(attrCount + 1); attrStarts[attrCount] = as; attrEnds[attrCount] = ae;
            attrLocalStarts[attrCount] = al; attrHashes[attrCount++] = ah;
            skipSpace(); expect('='); skipSpace(); byte quote = peek(0);
            if (quote != '\'' && quote != '"') fail("Attribute value must be quoted", p);
            long valueStart = ++p; p = findByte(p, end, quote);
            if (p == end) fail("Unclosed attribute value", valueStart);
            attrValueEntities[attrCount - 1] = validateEntities(valueStart, p);
            attrValueStarts[attrCount - 1] = valueStart; attrValueEnds[attrCount - 1] = p; p++;
            separated = p < end && space(b(p)); skipSpace();
        }
        boolean empty = p < end && b(p) == '/'; if (empty) p++; expect('>');
        if (depth == 0) { if (rootSeen) fail("Multiple root elements", markup); rootSeen = true; }
        emit(XmlEvent.START_ELEMENT, name.reset(in, ns, ne, nl, false), null, attrCount);
        if (empty) emit(XmlEvent.END_ELEMENT, name.reset(in, ns, ne, nl, false), null, 0);
        else {
            if (depth >= maxDepth) fail("Maximum depth exceeded", markup);
            ensureDepth(depth + 1); starts[depth] = ns; ends[depth] = ne; hashes[depth] = hash; depth++;
        }
    }

    private void close() {
        long markup = p; p += 2; long ns = p; int hash = scanName(); long ne = p; long nl = lastLocalStart;
        skipSpace(); expect('>');
        if (depth == 0) fail("Unexpected closing tag", markup);
        int top = depth - 1;
        if (hashes[top] != hash || ends[top] - starts[top] != ne - ns || !same(starts[top], ns, ne - ns))
            fail("Mismatched closing tag", markup);
        depth = top; emit(XmlEvent.END_ELEMENT, name.reset(in, ns, ne, nl, false), null, 0);
    }

    private void comment() {
        long from = p + 4, close = find(from, XmlByteUtils.COMMENT_CLOSE);
        if (close < 0) fail("Unclosed comment", p);
        for (long i = from; i + 1 < close; i++) if (b(i) == '-' && b(i + 1) == '-') fail("Invalid '--' in comment", i);
        p = close + 3; emit(XmlEvent.COMMENT, null, text.reset(in, from, close), 0);
    }
    private void cdata() {
        if (depth == 0) fail("CDATA outside root element", p);
        long from = p + 9, close = find(from, XmlByteUtils.CDATA_CLOSE);
        if (close < 0) fail("Unclosed CDATA", p);
        p = close + 3; emit(XmlEvent.CDATA, null, text.reset(in, from, close, from, false), 0);
    }
    private void processingInstruction() {
        long offset = p; p += 2; long ns = p; scanName(); long ne = p;
        long from = p, close = find(p, XmlByteUtils.PI_CLOSE); if (close < 0) fail("Unclosed processing instruction", offset);
        while (from < close && space(b(from))) from++; long trimmed = close;
        while (trimmed > from && space(b(trimmed - 1))) trimmed--;
        p = close + 2;
        boolean declaration = asciiIgnoreCase(ns, ne, XmlByteUtils.XML_NAME);
        if (declaration) { if (offset != 0 && offset != 3) fail("XML declaration must be first", offset); return; }
        emit(XmlEvent.PROCESSING_INSTRUCTION, name.reset(in, ns, ne), text.reset(in, from, trimmed), 0);
    }

    private void emit(XmlEvent event, DirectXmlByteSlice eventName, DirectXmlByteSlice eventText, int attributeCount) {
        if (extendedConsumer != null) extendedConsumer.onEvent(event, eventName, eventText,
                attributes.reset(in, attrStarts, attrEnds, attrValueStarts, attrValueEnds,
                        attrLocalStarts, attrValueEntities, attributeCount));
        else consumer.onEvent(event, eventName, eventText);
    }

    /**
     * Hashes the name and records where its local part starts in {@link #lastLocalStart}. The scan
     * already visits every byte, so the colon costs nothing here; rediscovering it later in
     * {@code DirectXmlByteSlice.local} was the hottest frame of the whole Direct path.
     */
    private int scanName() {
        final long limit = end;
        long i = p;
        if (i >= limit || !nameStart(b(i))) fail("Expected XML name", i);
        int hash = 0x811c9dc5;
        long localStart = i;
        // Each byte was loaded twice before — once to hash it, once as the next loop condition.
        byte value = b(i);
        do {
            hash = (hash ^ (value & 0xff)) * 0x01000193;
            if (value == ':') localStart = i + 1;
            if (++i >= limit) break;
            value = b(i);
        }
        while (namePart(value));
        p = i;
        lastLocalStart = localStart;
        return hash;
    }
    /** Returns whether the run contains at least one entity reference; the binder reuses the answer. */
    private boolean validateEntities(long from, long to) {
        // Process entities in batches to reduce findByte calls
        long i = from;
        boolean any = false;
        while (i < to) {
            long ampPos = findByte(i, to, (byte)'&');
            if (ampPos >= to) break; // No more '&' found
            any = true;

            long semi = ampPos + 1;
            while (semi < to && b(semi) != ';') semi++;
            if (semi == to) fail("Unclosed entity reference", ampPos);
            if (!entity(ampPos + 1, semi)) fail("Unknown or malformed entity reference", ampPos);
            i = semi + 1;
        }
        return any;
    }
    private boolean entity(long from, long to) {
        if (from >= to) return false;
        if (b(from) != '#') {
            // One length dispatch instead of rescanning the reference against five literals.
            switch ((int) (to - from)) {
                case 2: return matches(from, LT) || matches(from, GT);
                case 3: return matches(from, AMP);
                case 4: return matches(from, APOS) || matches(from, QUOT);
                default: return false;
            }
        }
        long i = from + 1; int radix = 10;
        if (i < to && (b(i) == 'x' || b(i) == 'X')) { radix = 16; i++; }
        if (i == to) return false; int cp = 0;
        for (; i < to; i++) {
            int value = b(i) & 0xff;
            int digit = value >= '0' && value <= '9' ? value - '0' : value >= 'a' && value <= 'f' ? value - 'a' + 10 : value >= 'A' && value <= 'F' ? value - 'A' + 10 : -1;
            if (digit < 0 || digit >= radix || cp > (0x10ffff - digit) / radix) return false;
            cp = cp * radix + digit;
        }
        return cp == 9 || cp == 10 || cp == 13 || cp >= 0x20 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF;
    }

    private void validateUtf8() {
        // Process 64 bytes at a time for better throughput
        long i = 0;
        while (i + 64 <= end) {
            long high1 = word(i) | word(i + 8) | word(i + 16) | word(i + 24);
            long high2 = word(i + 32) | word(i + 40) | word(i + 48) | word(i + 56);
            if (((high1 | high2) & HIGH_BITS) != 0) break;
            i += 64;
        }
        // Process remaining 32-byte chunks
        while (i + 32 <= end) {
            long high = word(i) | word(i + 8) | word(i + 16) | word(i + 24);
            if ((high & HIGH_BITS) != 0) break;
            i += 32;
        }
        while (i < end) {
            if (i + 8 <= end && (word(i) & HIGH_BITS) == 0) { i += 8; continue; }
            int b1 = b(i) & 0xff;
            if (b1 < 0x80) { i++; continue; }
            int width = b1 >= 0xc2 && b1 <= 0xdf ? 2 : b1 >= 0xe0 && b1 <= 0xef ? 3 : b1 >= 0xf0 && b1 <= 0xf4 ? 4 : 0;
            if (width == 0 || i + width > end) fail("Invalid UTF-8", i);
            int b2 = b(i + 1) & 0xff;
            int min = b1 == 0xe0 ? 0xa0 : b1 == 0xf0 ? 0x90 : 0x80;
            int max = b1 == 0xed ? 0x9f : b1 == 0xf4 ? 0x8f : 0xbf;
            if (b2 < min || b2 > max || width > 2 && !continuation(b(i + 2)) || width > 3 && !continuation(b(i + 3))) fail("Invalid UTF-8", i);
            i += width;
        }
    }

    private long lastLocalStart;
    private byte b(long at) { return DirectUnsafeAccess.getByte(in, at); }
    private long word(long at) { return DirectUnsafeAccess.getLong(in, at); }
    private byte peek(long delta) { return p + delta < end ? b(p + delta) : 0; }
    private void skipSpace() { while (p < end && space(b(p))) p++; }
    private void expect(char expected) { if (p >= end || b(p) != (byte)expected) fail("Expected '" + expected + "'", p); p++; }
    private boolean onlySpace(long from, long to) { for (long i = from; i < to; i++) if (!space(b(i))) return false; return true; }
    private boolean starts(long at, byte[] token) { if (at < 0 || at + token.length > end) return false; for (int i=0;i<token.length;i++) if(b(at+i)!=token[i]) return false; return true; }
    private boolean startsIgnoreCase(long at, byte[] token) { if (at + token.length > end) return false; return asciiIgnoreCase(at, at + token.length, token); }
    private long find(long from, byte[] token) {
        long limit = end - token.length + 1;
        for (long candidate = findByte(from, limit, token[0]); candidate < limit;
             candidate = findByte(candidate + 1, limit, token[0])) {
            int j = 1; while (j < token.length && b(candidate + j) == token[j]) j++;
            if (j == token.length) return candidate;
        }
        return -1;
    }
    private long findByte(long from, long to, byte target) {
        if (indexed && target == '<') return indexedNext(markupPositions, markupSize, true, from, to);
        if (indexed && target == '&') return indexedNext(entityPositions, entitySize, false, from, to);
        return activeFinder.find(in, from, to, target);
    }
    private void buildStructuralIndex() {
        markupSize = entitySize = markupCursor = entityCursor = 0;
        long less = ('<' & 0xffL) * 0x0101010101010101L;
        long amp = ('&' & 0xffL) * 0x0101010101010101L;
        long ones = 0x0101010101010101L, highs = 0x8080808080808080L;
        long i = 0;
        for (; i + 8 <= end; i += 8) {
            long word = word(i), x1 = word ^ less, x2 = word ^ amp;
            long matches = ((x1 - ones) & ~x1 & highs) | ((x2 - ones) & ~x2 & highs);
            if (matches == 0) continue;
            for (int lane = 0; lane < 8; lane++) {
                byte value = b(i + lane);
                if (value == '<') addMarkup(i + lane); else if (value == '&') addEntity(i + lane);
            }
        }
        for (; i < end; i++) { byte value = b(i); if (value == '<') addMarkup(i); else if (value == '&') addEntity(i); }
    }
    private long indexedNext(long[] positions, int size, boolean markup, long from, long to) {
        int cursor = markup ? markupCursor : entityCursor;
        while (cursor < size && positions[cursor] < from) cursor++;
        long result = cursor < size && positions[cursor] < to ? positions[cursor] : to;
        if (result < to) cursor++;
        if (markup) markupCursor = cursor; else entityCursor = cursor;
        return result;
    }
    private void addMarkup(long position) {
        if (markupSize == markupPositions.length) markupPositions = Arrays.copyOf(markupPositions, markupSize << 1);
        markupPositions[markupSize++] = position;
    }
    private void addEntity(long position) {
        if (entitySize == entityPositions.length) entityPositions = Arrays.copyOf(entityPositions, entitySize << 1);
        entityPositions[entitySize++] = position;
    }
    private static DirectByteFinder selectFinder() {
        String requested = System.getProperty("org.simdxml.direct.strategy", "auto");
        if (requested.equals("swar") || requested.equals("scalar")) return new ScalarDirectByteFinder();
        boolean forced = requested.equals("vector");
        try {
            Class<?> type = Class.forName("org.simdxml.VectorDirectByteFinder");
            // On a 128-bit species the vector finder has measured slower than SWAR-64 at every
            // document size, so "auto" keeps SWAR there. -Dorg.simdxml.direct.strategy=vector
            // still forces it, which is what the A/B benchmark uses.
            if (!forced && !((Boolean) type.getDeclaredMethod("beatsSwar").invoke(null)).booleanValue())
                return new ScalarDirectByteFinder();
            return (DirectByteFinder) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            if (forced) throw new IllegalStateException("Direct Vector backend unavailable", unavailable);
            return new ScalarDirectByteFinder();
        }
    }
    private static final byte[] LT={'l','t'}, GT={'g','t'}, AMP={'a','m','p'}, APOS={'a','p','o','s'}, QUOT={'q','u','o','t'};

    private boolean matches(long from, byte[] token) {
        for (int i = 0; i < token.length; i++) if (b(from + i) != token[i]) return false;
        return true;
    }

    /**
     * Word-at-a-time name comparison. Two words read the same way are equal in exactly the same
     * cases whichever endianness the machine uses — equality, unlike ordering, needs no assumption
     * about byte order — so this stays correct while cutting the per-byte reads by eight.
     */
    private boolean same(long a,long c,long length){
        long i = 0;
        for (; i + 8 <= length; i += 8)
            if (DirectUnsafeAccess.getLong(in, a + i) != DirectUnsafeAccess.getLong(in, c + i)) return false;
        for (; i < length; i++) if (b(a + i) != b(c + i)) return false;
        return true;
    }
    private boolean asciiIgnoreCase(long from,long to,byte[] value){if(to-from!=value.length)return false;for(int i=0;i<value.length;i++){int x=b(from+i)&255,y=value[i]&255;if(x>='a'&&x<='z')x-=32;if(x!=y)return false;}return true;}
    private void ensureDepth(int needed){if(needed<=starts.length)return;int n=Math.min(maxDepth,Math.max(needed,starts.length<<1));starts=Arrays.copyOf(starts,n);ends=Arrays.copyOf(ends,n);hashes=Arrays.copyOf(hashes,n);}
    private void ensureAttrs(int needed){if(needed<=attrStarts.length)return;int n=attrStarts.length<<1;while(n<needed)n<<=1;attrStarts=Arrays.copyOf(attrStarts,n);attrEnds=Arrays.copyOf(attrEnds,n);attrValueStarts=Arrays.copyOf(attrValueStarts,n);attrValueEnds=Arrays.copyOf(attrValueEnds,n);attrHashes=Arrays.copyOf(attrHashes,n);attrLocalStarts=Arrays.copyOf(attrLocalStarts,n);attrValueEntities=Arrays.copyOf(attrValueEntities,n);}
    private static boolean continuation(byte value){return(value&0xc0)==0x80;}
    private static boolean space(byte value){return value==' '||value=='\t'||value=='\n'||value=='\r';}
    private static boolean letter(byte value){return value>='A'&&value<='Z'||value>='a'&&value<='z';}
    private static boolean nameStart(byte value){return value==':'||value=='_'||letter(value)||value<0;}
    private static boolean namePart(byte value){return nameStart(value)||value=='-'||value=='.'||value>='0'&&value<='9';}
    private static void fail(String message,long offset){throw new XmlParsingException(message,Math.toIntExact(Math.min(Integer.MAX_VALUE,offset)));}
}
