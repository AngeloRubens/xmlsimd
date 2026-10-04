package org.simdxml;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/** Forward-only, allocation-conscious XML reader backed by SIMD structural indexes. */
public final class SimdXmlStreamReader implements BindingXmlReader {
    private byte[] in;
    private int end;
    private final int maxDepth;
    private final StructuralIndex index;
    private final ByteNameCache nameCache;
    /* 1BRC-style compact key stack: retain byte slices and hashes, not one String/object per level. */
    private long[] elementSlices = new long[16];
    private int[] elementHashes = new int[16];
    private int depth;
    private int p;
    private boolean started;
    private boolean ended;
    private boolean rootSeen;
    private boolean pendingEmptyEnd;
    private int pendingNameStart, pendingNameEnd, pendingNameHash, pendingLocalStart, pendingLocalHash;
    private XmlEvent event;
    private String name;
    private int eventNameStart = -1;
    private int eventNameEnd = -1;
    private int eventNameHash;
    private int eventLocalNameStart;
    private int eventLocalNameHash;
    private String text;
    private int textStart = -1;
    private int textEnd = -1;
    private Map<String, String> attributes = java.util.Collections.emptyMap();
    private int attributeCount;
    private int[] attributeNameStarts = new int[8], attributeNameEnds = new int[8];
    private int[] attributeValueStarts = new int[8], attributeValueEnds = new int[8];
    private boolean namespaceDeclarations;
    private final XmlByteSlice nameSlice = new XmlByteSlice();
    private final XmlByteSlice textSlice = new XmlByteSlice();
    private final XmlByteSlice attributeSlice = new XmlByteSlice();

    SimdXmlStreamReader(byte[] input, int length, int maxDepth, StructuralIndex index) {
        this(input, length, maxDepth, index, false);
    }

    SimdXmlStreamReader(byte[] input, int length, int maxDepth, StructuralIndex index, boolean persistentSymbols) {
        this.maxDepth = maxDepth;
        this.index = index;
        this.nameCache = new ByteNameCache(persistentSymbols);
        reset(input, length);
    }

    void reset(byte[] input, int length) {
        this.in = input;
        this.end = length;
        depth = p = 0;
        started = ended = rootSeen = pendingEmptyEnd = false;
        event = null;
        pendingNameStart = pendingNameEnd = pendingNameHash = pendingLocalStart = pendingLocalHash = 0;
        clearEventData();
        nameCache.clear();
        if (startsRaw(0, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) p = 3;
    }

    public XmlEvent event() { return event; }
    public String name() {
        require(XmlEvent.START_ELEMENT, XmlEvent.END_ELEMENT, XmlEvent.PROCESSING_INSTRUCTION);
        if (name == null) name = nameCache.intern(in, eventNameStart, eventNameEnd, eventNameHash);
        return name;
    }
    public String text() {
        require(XmlEvent.TEXT, XmlEvent.CDATA, XmlEvent.COMMENT, XmlEvent.PROCESSING_INSTRUCTION);
        if (text == null) text = raw(textStart, textEnd);
        return text;
    }
    /** Raw UTF-8 qualified-name bytes; the returned flyweight changes on the next event/reset. */
    public XmlByteSlice nameBytes() {
        require(XmlEvent.START_ELEMENT, XmlEvent.END_ELEMENT, XmlEvent.PROCESSING_INSTRUCTION);
        return nameSlice.reset(in, eventNameStart, eventNameEnd);
    }
    /** Raw XML text bytes before entity expansion; the flyweight changes on the next event/reset. */
    @Override public XmlByteSlice rawTextBytes() {
        require(XmlEvent.TEXT, XmlEvent.CDATA, XmlEvent.COMMENT, XmlEvent.PROCESSING_INSTRUCTION);
        return textSlice.reset(in, textStart, textEnd);
    }
    public Map<String, String> attributes() {
        require(XmlEvent.START_ELEMENT);
        if (attributes == null) {
            SmallAttributeMap result = new SmallAttributeMap();
            for (int i = 0; i < attributeCount; i++) {
                String name = raw(attributeNameStarts[i], attributeNameEnds[i]);
                if (!result.putUnique(name, decode(attributeValueStarts[i], attributeValueEnds[i])))
                    throw new XmlParsingException("Duplicate attribute: " + name, attributeNameStarts[i]);
            }
            attributes = result;
        }
        return attributes;
    }
    /** Unqualified binding lookup; compares directly against input bytes and decodes only the value. */
    public String attribute(String name) {
        require(XmlEvent.START_ELEMENT);
        for (int i = 0; i < attributeCount; i++)
            if (asciiEquals(name, attributeNameStarts[i], attributeNameEnds[i]))
                return decode(attributeValueStarts[i], attributeValueEnds[i]);
        return null;
    }
    @Override public XmlByteSlice rawAttributeBytes(String name) {
        require(XmlEvent.START_ELEMENT);
        for (int i = 0; i < attributeCount; i++)
            if (asciiEquals(name, attributeNameStarts[i], attributeNameEnds[i]))
                return attributeSlice.reset(in, attributeValueStarts[i], attributeValueEnds[i]);
        return null;
    }
    @Override public boolean hasNamespaceDeclarations() { require(XmlEvent.START_ELEMENT); return namespaceDeclarations; }
    public boolean hasNext() { return !ended; }
    @Override public boolean hasByteNames() { return true; }
    @Override public int localNameHash() { return eventLocalNameHash; }
    @Override public int localNameLength() { return eventNameEnd - eventLocalNameStart; }
    long localNamePrefix8() {
        int length = Math.min(8, localNameLength());
        long packed = 0;
        for (int i = 0; i < length; i++) packed |= (long) (in[eventLocalNameStart + i] & 0xff) << (i << 3);
        return packed;
    }
    long localNameSuffix8() {
        int nameLength = localNameLength(), length = Math.min(8, nameLength);
        int start = eventNameEnd - length;
        long packed = 0;
        for (int i = 0; i < length; i++) packed |= (long) (in[start + i] & 0xff) << (i << 3);
        return packed;
    }
    boolean localNameEquals(byte[] ascii) {
        if (eventNameStart < 0) return false;
        if (eventNameEnd - eventLocalNameStart != ascii.length) return false;
        for (int i = 0; i < ascii.length; i++) if (in[eventLocalNameStart + i] != ascii[i]) return false;
        return true;
    }
    @Override public boolean localNameEquals(String ascii) {
        if (eventNameStart < 0 || eventNameEnd - eventLocalNameStart != ascii.length()) return false;
        for (int i = 0; i < ascii.length(); i++) if ((byte) ascii.charAt(i) != in[eventLocalNameStart + i]) return false;
        return true;
    }
    /** Byte-to-byte comparison; the binder holds precomputed ASCII names, so no char decoding. */
    @Override public boolean localNameEqualsBytes(byte[] ascii) {
        return eventNameStart >= 0 && byteRangeEquals(in, eventLocalNameStart,
                eventLocalNameStart + ascii.length, ascii, 0, ascii.length);
    }
    @Override public XmlByteSlice rawAttributeBytes(byte[] name) {
        require(XmlEvent.START_ELEMENT);
        int index = attributeIndex(name);
        return index < 0 ? null : attributeSlice.reset(in, attributeValueStarts[index], attributeValueEnds[index]);
    }
    @Override public String attribute(byte[] name) {
        require(XmlEvent.START_ELEMENT);
        int index = attributeIndex(name);
        return index < 0 ? null : decode(attributeValueStarts[index], attributeValueEnds[index]);
    }
    private int attributeIndex(byte[] name) {
        for (int i = 0; i < attributeCount; i++)
            if (byteRangeEquals(in, attributeNameStarts[i], attributeNameEnds[i], name, 0, name.length))
                return i;
        return -1;
    }

    public XmlEvent next() {
        if (ended) throw new IllegalStateException("Reader is at END_DOCUMENT");
        clearEventData();
        if (!started) { started = true; return event = XmlEvent.START_DOCUMENT; }
        if (pendingEmptyEnd) {
            pendingEmptyEnd = false;
            eventNameStart = pendingNameStart; eventNameEnd = pendingNameEnd;
            eventNameHash = pendingNameHash; eventLocalNameStart = pendingLocalStart;
            eventLocalNameHash = pendingLocalHash;
            return event = XmlEvent.END_ELEMENT;
        }
        while (p < end) {
            int markup = in[p] == '<' ? p : index.next(p, (byte)'<', in);
            if (markup > p) {
                int start = p; p = markup;
                int amp = index.next(start, markup, (byte)'&', in);
                if (depth != 0) {
                    textStart = start; textEnd = markup;
                    if (amp < markup) text = decode(start, markup);
                    return event = XmlEvent.TEXT;
                }
                if (!onlySpace(start, markup)) fail("Character data outside root element", start);
            }
            if (p >= end) break;
            byte kind = peek(1);
            if (kind == '?') {
                XmlEvent pi = processingInstruction();
                if (pi != null) return pi;
                continue;
            }
            if (kind == '/') return closeElement();
            if (kind == '!') {
                if (starts(p, XmlByteUtils.COMMENT_OPEN)) return comment();
                if (starts(p, XmlByteUtils.CDATA_OPEN)) return cdata();
                if (startsIgnoreCase(p, XmlByteUtils.DOCTYPE_OPEN))
                    fail("DTDs and external entities are not supported", p);
                fail("Unsupported declaration", p);
            }
            return openElement();
        }
        if (depth != 0) fail("Unclosed element <" + raw(elementStart(depth - 1), elementEnd(depth - 1)) + ">", end);
        if (!rootSeen) fail("Document has no root element", end);
        ended = true;
        return event = XmlEvent.END_DOCUMENT;
    }

    private XmlEvent openElement() {
        name = null; // a skipped XML declaration may have populated the same event slot
        int start = p++;
        int nameStart = p;
        int nameHash = scanName();
        int nameLocalStart = eventLocalNameStart, nameLocalHash = eventLocalNameHash;
        int nameEnd = p;
        eventNameStart = nameStart; eventNameEnd = nameEnd; eventNameHash = nameHash;
        boolean separated = p < end && isSpace(in[p]);
        attributeCount = 0;
        attributes = null;
        skipSpace();
        while (p < end && in[p] != '>' && !(in[p] == '/' && peek(1) == '>')) {
            if (!separated) fail("Whitespace required before attribute", p);
            int attrStart = p;
            int attrNameStart = p; scanName(); int attrNameEnd = p;
            if (asciiEquals("xmlns", attrNameStart, attrNameEnd) || startsWithAscii("xmlns:", attrNameStart, attrNameEnd)) namespaceDeclarations = true;
            skipSpace(); expect('='); skipSpace();
            byte quote = peek(0);
            if (quote != '\'' && quote != '"') fail("Attribute value must be quoted", p);
            int valueStart = ++p;
            while (p < end && in[p] != quote) p++;
            if (p == end) fail("Unclosed attribute value", valueStart);
            for (int i = 0; i < attributeCount; i++)
                if (byteRangeEquals(in, attributeNameStarts[i], attributeNameEnds[i], in, attrNameStart, attrNameEnd))
                    fail("Duplicate attribute: " + raw(attrNameStart, attrNameEnd), attrStart);
            ensureAttributeCapacity(attributeCount + 1);
            attributeNameStarts[attributeCount] = attrNameStart; attributeNameEnds[attributeCount] = attrNameEnd;
            attributeValueStarts[attributeCount] = valueStart; attributeValueEnds[attributeCount] = p;
            attributeCount++;
            p++; separated = p < end && isSpace(in[p]); skipSpace();
        }
        boolean empty = p < end && in[p] == '/';
        if (empty) p++;
        expect('>');
        if (depth == 0) {
            if (rootSeen) fail("Multiple root elements", start);
            rootSeen = true;
        }
        if (empty) {
            pendingEmptyEnd = true;
            pendingNameStart = nameStart; pendingNameEnd = nameEnd;
            pendingNameHash = nameHash; pendingLocalStart = nameLocalStart; pendingLocalHash = nameLocalHash;
        }
        else {
            if (depth >= maxDepth) fail("Maximum depth of " + maxDepth + " exceeded", start);
            ensureElementCapacity(depth + 1);
            elementSlices[depth] = ((long) nameStart << 32) | (nameEnd & 0xffffffffL);
            elementHashes[depth] = nameHash;
            depth++;
        }
        eventLocalNameStart = nameLocalStart; eventLocalNameHash = nameLocalHash;
        return event = XmlEvent.START_ELEMENT;
    }

    private XmlEvent closeElement() {
        name = null;
        int start = p; p += 2;
        int closeNameStart = p;
        int closeHash = scanName();
        int closeNameEnd = p;
        eventNameStart = closeNameStart; eventNameEnd = closeNameEnd; eventNameHash = closeHash;
        skipSpace(); expect('>');
        if (depth == 0) fail("Unexpected closing tag </" + raw(closeNameStart, closeNameEnd) + ">", start);
        int top = depth - 1;
        if (elementHashes[top] != closeHash
                || elementEnd(top) - elementStart(top) != closeNameEnd - closeNameStart
                || !sameBytes(elementStart(top), closeNameStart, closeNameEnd - closeNameStart)) {
            fail("Expected </" + raw(elementStart(top), elementEnd(top)) + "> but found </" + raw(closeNameStart, closeNameEnd) + ">", start);
        }
        depth = top;
        return event = XmlEvent.END_ELEMENT;
    }

    private XmlEvent comment() {
        int start = p + 4, close = find(start, XmlByteUtils.COMMENT_CLOSE);
        if (close < 0) fail("Unclosed comment", p);
        for (int i = start; i + 1 < close; i++)
            if (in[i] == '-' && in[i + 1] == '-') fail("'--' is not allowed inside comments", start);
        textStart = start; textEnd = close;
        p = close + 3; return event = XmlEvent.COMMENT;
    }

    private XmlEvent cdata() {
        if (depth == 0) fail("CDATA outside root element", p);
        int start = p + 9, close = find(start, XmlByteUtils.CDATA_CLOSE);
        if (close < 0) fail("Unclosed CDATA section", p);
        textStart = start; textEnd = close; p = close + 3; return event = XmlEvent.CDATA;
    }

    private XmlEvent processingInstruction() {
        int offset = p; p += 2;
        int nameStart = p, nameHash = scanName();
        eventNameStart = nameStart; eventNameEnd = p; eventNameHash = nameHash;
        name = nameCache.intern(in, nameStart, p, nameHash);
        int dataStart = p, close = find(p, XmlByteUtils.PI_CLOSE);
        if (close < 0) fail("Unclosed processing instruction", offset);
        int rawClose = close;
        while (dataStart < close && isSpace(in[dataStart])) dataStart++;
        while (close > dataStart && isSpace(in[close - 1])) close--;
        textStart = dataStart; textEnd = close; p = rawClose + 2;
        text = null;
        if (name.equalsIgnoreCase("xml")) {
            if (offset != 0 && offset != 3) fail("XML declaration must be first", offset);
            return null;
        }
        return event = XmlEvent.PROCESSING_INSTRUCTION;
    }

    private void clearEventData() {
        name = null; text = null; textStart = textEnd = -1; attributes = java.util.Collections.emptyMap(); attributeCount = 0;
        namespaceDeclarations = false;
        eventNameStart = eventNameEnd = eventLocalNameStart = -1; eventNameHash = eventLocalNameHash = 0;
    }
    private void require(XmlEvent... allowed) {
        for (XmlEvent candidate : allowed) if (event == candidate) return;
        throw new IllegalStateException("Property is not available for event " + event);
    }
    private String readName() {
        int start = p; int hash = scanName(); return nameCache.intern(in, start, p, hash);
    }
    /** FNV-1a is accumulated for free while the delimiter-seeking name loop is already hot. */
    private int scanName() {
        final byte[] input = in;
        final int limit = end;
        int i = p;
        if (i >= limit || !nameStart(input[i])) fail("Expected XML name", i);
        int start = i;
        int hash = 0x811c9dc5;
        int localStart = -1;
        /*
         * One multiply chain per byte: qualified names are rare, so the local hash is deferred.
         * The byte is kept in a local and the cursor stays in a register: the previous shape read
         * every byte twice, once to hash it and once as the next iteration's loop condition.
         */
        byte value = input[i];
        do {
            hash = (hash ^ (value & 0xff)) * 0x01000193;
            if (value == ':') localStart = i + 1;
            if (++i >= limit) break;
            value = input[i];
        }
        while (namePart(value));
        p = i;
        if (localStart < 0) { eventLocalNameStart = start; eventLocalNameHash = hash; return hash; }
        int localHash = 0x811c9dc5;
        for (int at = localStart; at < p; at++) localHash = (localHash ^ (in[at] & 0xff)) * 0x01000193;
        eventLocalNameStart = localStart; eventLocalNameHash = localHash;
        return hash;
    }
    private boolean sameBytes(int left, int right, int length) {
        for (int i = 0; i < length; i++) if (in[left + i] != in[right + i]) return false;
        return true;
    }
    private boolean asciiEquals(String value, int from, int to) {
        if (to - from != value.length()) return false;
        for (int i = 0; i < value.length(); i++) if ((byte) value.charAt(i) != in[from + i]) return false;
        return true;
    }
    private boolean startsWithAscii(String value, int from, int to) {
        return to - from >= value.length() && asciiEquals(value, from, from + value.length());
    }
    private static boolean byteRangeEquals(byte[] left, int leftFrom, int leftTo,
            byte[] right, int rightFrom, int rightTo) {
        int length = leftTo - leftFrom;
        if (length != rightTo - rightFrom) return false;
        for (int i = 0; i < length; i++)
            if (left[leftFrom + i] != right[rightFrom + i]) return false;
        return true;
    }
    private void ensureAttributeCapacity(int needed) {
        if (needed <= attributeNameStarts.length) return;
        int capacity = attributeNameStarts.length << 1;
        attributeNameStarts = Arrays.copyOf(attributeNameStarts, capacity); attributeNameEnds = Arrays.copyOf(attributeNameEnds, capacity);
        attributeValueStarts = Arrays.copyOf(attributeValueStarts, capacity); attributeValueEnds = Arrays.copyOf(attributeValueEnds, capacity);
    }
    private boolean onlySpace(int from, int to) {
        for (int i = from; i < to; i++) if (!isSpace(in[i])) return false;
        return true;
    }
    private void ensureElementCapacity(int needed) {
        if (needed <= elementSlices.length) return;
        int capacity = Math.min(maxDepth, Math.max(needed, elementSlices.length << 1));
        elementSlices = Arrays.copyOf(elementSlices, capacity);
        elementHashes = Arrays.copyOf(elementHashes, capacity);
    }
    private int elementStart(int level) { return (int) (elementSlices[level] >>> 32); }
    private int elementEnd(int level) { return (int) elementSlices[level]; }
    private boolean nameStart(byte b) { return b == ':' || b == '_' || asciiLetter(b) || b < 0; }
    private boolean namePart(byte b) { return nameStart(b) || b == '-' || b == '.' || b >= '0' && b <= '9'; }
    private boolean asciiLetter(byte b) { return b >= 'A' && b <= 'Z' || b >= 'a' && b <= 'z'; }
    private boolean isSpace(byte b) { return b == ' ' || b == '\t' || b == '\n' || b == '\r'; }
    private void skipSpace() { while (p < end && isSpace(in[p])) p++; }
    private void expect(char c) { if (p >= end || in[p] != (byte)c) fail("Expected '" + c + "'", p); p++; }
    private byte peek(int delta) { return p + delta < end ? in[p + delta] : 0; }
    private boolean startsRaw(int at, byte... bytes) {
        if (at + bytes.length > end) return false;
        for (int i = 0; i < bytes.length; i++) if (in[at + i] != bytes[i]) return false;
        return true;
    }
    private boolean starts(int at, byte[] token) { return XmlByteUtils.starts(in, end, at, token); }
    private boolean startsIgnoreCase(int at, byte[] token) { return XmlByteUtils.startsIgnoreAsciiCase(in, end, at, token); }
    private int find(int from, byte[] needle) { return XmlByteUtils.find(in, end, from, needle); }
    private String raw(int from, int to) { return new String(in, from, to - from, StandardCharsets.UTF_8); }
    /**
     * Entity expansion scans its own range directly. The structural index exposes a forward-only
     * cursor, so a second query over an already-visited range would report no entity at all.
     */
    private int indexOfByte(int from, int to, byte token) {
        for (int i = from; i < to; i++) if (in[i] == token) return i;
        return to;
    }
    private String decode(int from, int to) {
        int amp = indexOfByte(from, to, (byte)'&');
        if (amp >= to) return raw(from, to);
        StringBuilder out = new StringBuilder(to - from); int cursor = from;
        while (amp < to) {
            out.append(raw(cursor, amp)); int semi = amp + 1;
            while (semi < to && in[semi] != ';') semi++;
            if (semi == to) fail("Unclosed entity reference", amp);
            String entity = raw(amp + 1, semi);
            if ("lt".equals(entity)) out.append('<'); else if ("gt".equals(entity)) out.append('>');
            else if ("amp".equals(entity)) out.append('&'); else if ("apos".equals(entity)) out.append('\'');
            else if ("quot".equals(entity)) out.append('"'); else out.append(numericEntity(entity, amp));
            cursor = semi + 1; amp = indexOfByte(cursor, to, (byte)'&');
        }
        return out.append(raw(cursor, to)).toString();
    }
    private String numericEntity(String entity, int offset) {
        if (!entity.startsWith("#")) { fail("Unknown entity '&" + entity + ";'", offset); return ""; }
        try {
            boolean hex = entity.startsWith("#x") || entity.startsWith("#X");
            int cursor = hex ? 2 : 1, radix = hex ? 16 : 10, cp = 0;
            if (cursor == entity.length()) throw new NumberFormatException();
            for (; cursor < entity.length(); cursor++) {
                int digit = Character.digit(entity.charAt(cursor), radix);
                if (digit < 0 || cp > (0x10ffff - digit) / radix) throw new NumberFormatException();
                cp = cp * radix + digit;
            }
            if (!(cp == 9 || cp == 10 || cp == 13 || cp >= 0x20 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF))
                fail("Invalid XML character reference", offset);
            return new String(Character.toChars(cp));
        } catch (NumberFormatException e) { fail("Malformed character reference", offset); return ""; }
    }
    private void fail(String message, int offset) { throw new XmlParsingException(message, offset); }
}
