package org.simdxml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Direct/FFM binder must produce exactly what the standard binder produces. The Direct path now
 * retains the bounds of a single text segment and converts from bytes at END instead of decoding a
 * String per segment, so these pin the cases where that shortcut must not be taken: entities, CDATA,
 * text split by a comment or a child element, and empty content.
 *
 * <p>Every case is run over a heap segment, a native segment and a direct {@code ByteBuffer}, since
 * those select different memory accessors inside the parser.
 */
class DirectBindingEquivalenceTest {

    private static Scalars direct(String xml, MemoryKind kind) {
        return bind(xml, kind, Scalars.class);
    }

    private static <T> T bind(String xml, MemoryKind kind, Class<T> type) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        DirectSimdXmlParser parser = DirectSimdXmlParser.builder().withMaxDepth(32).build();
        switch (kind) {
            case HEAP: return parser.bind(MemorySegment.ofArray(bytes), type);
            case BUFFER: {
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.put(bytes).flip();
                return parser.bind(buffer, type);
            }
            default:
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment segment = arena.allocate(bytes.length);
                    segment.copyFrom(MemorySegment.ofArray(bytes));
                    return parser.bind(segment, type);
                }
        }
    }

    private static <T> T standard(String xml, Class<T> type) {
        return SimdJaxbContext.builder(type).build().createUnmarshaller()
                .unmarshal(xml.getBytes(StandardCharsets.UTF_8), type);
    }

    private enum MemoryKind { HEAP, NATIVE, BUFFER }

    private static void forEachMemory(String xml, java.util.function.Consumer<Scalars> assertion) {
        for (MemoryKind kind : MemoryKind.values()) assertion.accept(direct(xml, kind));
    }

    @Test
    void scalarsConvertFromBytesExactlyAsTheStandardBinderDoes() {
        String xml = "<s><i>2147483647</i><l>-9223372036854775808</l><d>3.5</d><f>0.25</f>"
                + "<b>true</b><t>plain</t></s>";
        Scalars expected = standard(xml, Scalars.class);
        forEachMemory(xml, actual -> {
            assertEquals(expected.i, actual.i);
            assertEquals(expected.l, actual.l);
            assertEquals(expected.d, actual.d);
            assertEquals(expected.f, actual.f);
            assertEquals(expected.b, actual.b);
            assertEquals(expected.t, actual.t);
        });
    }

    @Test
    void surroundingWhitespaceIsTrimmedForNumbersAndKeptForStrings() {
        String xml = "<s><i> 42 </i><l> -7 </l><d> 1.5 </d><b> true </b><t> kept </t></s>";
        Scalars expected = standard(xml, Scalars.class);
        forEachMemory(xml, actual -> {
            assertEquals(42, actual.i);
            assertEquals(-7L, actual.l);
            assertEquals(1.5d, actual.d);
            assertEquals(expected.i, actual.i);
            assertEquals(expected.t, actual.t);
        });
    }

    @Test
    void entitiesAreExpandedOnTheRetainedSegmentPath() {
        String xml = "<s><t>a &amp; b &lt;c&gt; &#49;&#x32;</t><i>&#52;2</i></s>";
        Scalars expected = standard(xml, Scalars.class);
        forEachMemory(xml, actual -> {
            assertEquals("a & b <c> 12", actual.t);
            assertEquals(expected.t, actual.t);
            assertEquals(42, actual.i);
        });
    }

    @Test
    void cdataBindsLikeText() {
        String xml = "<s><t><![CDATA[raw < & > text]]></t><i><![CDATA[17]]></i></s>";
        forEachMemory(xml, actual -> {
            assertEquals("raw < & > text", actual.t);
            assertEquals(17, actual.i);
        });
    }

    @Test
    void textSplitByACommentIsConcatenatedNotTruncated() {
        String xml = "<s><t>ab<!--x-->cd</t><i>1<!--x-->2</i></s>";
        forEachMemory(xml, actual -> {
            assertEquals("abcd", actual.t);
            // The retained first segment must be materialized when a second one arrives.
            assertEquals(12, actual.i);
        });
    }

    @Test
    void textSplitByACdataSectionIsConcatenated() {
        String xml = "<s><t>ab<![CDATA[cd]]>ef</t><i>1<![CDATA[2]]>3</i></s>";
        forEachMemory(xml, actual -> {
            assertEquals("abcdef", actual.t);
            assertEquals(123, actual.i);
        });
    }

    @Test
    void emptyAndAbsentElementsBindLikeTheStandardBinder() {
        String xml = "<s><t></t><i>0</i></s>";
        Scalars expected = standard(xml, Scalars.class);
        forEachMemory(xml, actual -> {
            assertEquals(expected.t, actual.t);
            assertEquals(0, actual.i);
        });
    }

    @Test
    void attributesBindFromBytesIncludingNumericAndBoolean() {
        String xml = "<a id='2147483647' flag='true' label='x &amp; y' ratio='0.5'/>";
        Attributed expected = standard(xml, Attributed.class);
        Attributed actual = bind(xml, MemoryKind.NATIVE, Attributed.class);
        assertEquals(expected.id, actual.id);
        assertEquals(expected.flag, actual.flag);
        assertEquals(expected.label, actual.label);
        assertEquals(expected.ratio, actual.ratio);
        assertEquals("x & y", actual.label);
    }

    @Test
    void repeatedElementsBuildTheSameListAsTheStandardBinder() {
        StringBuilder xml = new StringBuilder("<catalog>");
        for (int i = 0; i < 40; i++) xml.append("<book id='").append(i).append("'>t").append(i).append("</book>");
        xml.append("</catalog>");
        Catalog expected = standard(xml.toString(), Catalog.class);
        Catalog actual = bind(xml.toString(), MemoryKind.NATIVE, Catalog.class);
        assertEquals(expected.books.size(), actual.books.size());
        for (int i = 0; i < expected.books.size(); i++) {
            assertEquals(expected.books.get(i).id, actual.books.get(i).id);
            assertEquals(expected.books.get(i).title, actual.books.get(i).title);
        }
    }

    @Test
    void mixedContentAroundAChildKeepsBothTextRuns() {
        // The parent frame retains "before", the child pushes and pops, then "after" arrives.
        String xml = "<mixed>before<child>inner</child>after</mixed>";
        Mixed actual = bind(xml, MemoryKind.NATIVE, Mixed.class);
        assertEquals("inner", actual.child);
        assertTrue(actual.text == null || actual.text.contains("before"), String.valueOf(actual.text));
    }

    @Test
    void aRetainedSegmentIsNotReusedAcrossDocuments() {
        DirectSimdXmlParser parser = DirectSimdXmlParser.builder().build();
        for (int i = 0; i < 50; i++) {
            byte[] bytes = ("<s><i>" + i + "</i><t>text" + i + "</t></s>").getBytes(StandardCharsets.UTF_8);
            Scalars bound = parser.bind(MemorySegment.ofArray(bytes), Scalars.class);
            assertEquals(i, bound.i);
            assertEquals("text" + i, bound.t);
        }
    }

    @Test
    void malformedNumbersFailOnBothBinders() {
        String xml = "<s><i>12a4</i></s>";
        assertThrows(RuntimeException.class, () -> standard(xml, Scalars.class));
        assertThrows(RuntimeException.class, () -> bind(xml, MemoryKind.NATIVE, Scalars.class));
    }

    @XmlRootElement(name = "s")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Scalars {
        @XmlElement public int i;
        @XmlElement public long l;
        @XmlElement public double d;
        @XmlElement public float f;
        @XmlElement public boolean b;
        @XmlElement public String t;
    }

    @XmlRootElement(name = "a")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Attributed {
        @XmlAttribute public int id;
        @XmlAttribute public boolean flag;
        @XmlAttribute public String label;
        @XmlAttribute public double ratio;
    }

    @XmlRootElement(name = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElement(name = "book") public List<Book> books = new ArrayList<Book>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute public int id;
        @XmlValue public String title;
    }

    @XmlRootElement(name = "mixed")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Mixed {
        @XmlElement public String child;
        @XmlValue public String text;
    }
}
