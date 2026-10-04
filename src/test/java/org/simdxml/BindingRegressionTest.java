package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions found while optimizing the binder: raw-byte scalar dispatch, byte-name attribute
 * matching and namespace handling must not change what the general path produces.
 * The annotations are fully qualified because this package also declares {@code XmlElement}.
 */
class BindingRegressionTest {
    private static <T> T bind(String xml, Class<T> type) {
        return SimdJaxbContext.builder(type).build().createUnmarshaller()
                .unmarshal(xml.getBytes(StandardCharsets.UTF_8), type);
    }

    @Test
    void attributeNameThatPrefixesAnEarlierNameIsNotADuplicate() {
        assertEquals("1", bind("<e abc='1' ab='2'/>", Attrs.class).abc);
        assertEquals("2", bind("<e abc='1' ab='2'/>", Attrs.class).ab);
        assertEquals("2", bind("<e ab='2' abc='1'/>", Attrs.class).ab);
        assertThrows(XmlParsingException.class, () -> bind("<e ab='1' ab='2'/>", Attrs.class));
    }

    @Test
    void scalarTextWithEntitiesKeepsEveryCharacter() {
        assertEquals(12, bind("<r><n>&#49;2</n></r>", Nums.class).n);
        assertEquals(-7, bind("<r><n> -7 </n></r>", Nums.class).n);
    }

    @Test
    void scalarTextSplitAcrossSegmentsIsConcatenatedNotTruncated() {
        assertEquals(1234, bind("<r><n>12<!--x-->34</n></r>", Nums.class).n);
        assertEquals(1234, bind("<r><n>12<![CDATA[34]]></n></r>", Nums.class).n);
        assertEquals(1234, bind("<r><n><![CDATA[12]]>34</n></r>", Nums.class).n);
    }

    @Test
    void entitiesAreExpandedAboveTheStructuralIndexThreshold() {
        // Documents larger than the tiny-document threshold take the indexed path, whose
        // forward-only entity cursor previously made a second lookup report "no entity".
        StringBuilder padded = new StringBuilder("<r>");
        for (int i = 0; i < 400; i++) padded.append("<pad>0123456789</pad>");
        padded.append("<text>A &lt;B&gt; C &amp; D</text></r>");
        assertTrue(padded.length() > 4096, "fixture must exceed the 4096-byte threshold");
        assertEquals("A <B> C & D", bind(padded.toString(), Entities.class).text);
        assertEquals("A <B> C & D", bind("<r><text>A &lt;B&gt; C &amp; D</text></r>", Entities.class).text);
    }

    @Test
    void scalarElementWithChildIsRejected() {
        XmlBindingException error = assertThrows(XmlBindingException.class,
                () -> bind("<r><n><x/></n></r>", Nums.class));
        assertTrue(error.getMessage().contains("contains child"), error.getMessage());
    }

    @Test
    void xsiNilIsHonouredWhateverThePrefixIs() {
        assertNull(bind("<r xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'><n xsi:nil='true'/></r>", Nil.class).n);
        assertNull(bind("<r xmlns:i='http://www.w3.org/2001/XMLSchema-instance'><n i:nil='true'/></r>", Nil.class).n);
        assertEquals(Integer.valueOf(5), bind("<r><n>5</n></r>", Nil.class).n);
    }

    @Test
    void namespacedChildDoesNotBindToAnUnqualifiedProperty() {
        assertEquals(0, bind("<r><child xmlns='urn:other'>x</child></r>", Kids.class).child.size());
        assertEquals(1, bind("<r><child>x</child></r>", Kids.class).child.size());
        // An xmlns declaration that binds only a prefix leaves unprefixed children unqualified.
        assertEquals(1, bind("<r xmlns:p='urn:other'><child>x</child></r>", Kids.class).child.size());
    }

    @Test
    void generatedAccessIsResolvedOncePerTypeAndCachesFailures() {
        assertNull(GeneratedAccessFactory.get(Getters.class), "no public fields: reflection fallback");
        assertNull(GeneratedAccessFactory.get(Getters.class), "the failed attempt must be cached");
        assertEquals("v", bind("<p><a>v</a></p>", Getters.class).getA());
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "e")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Attrs {
        @jakarta.xml.bind.annotation.XmlAttribute public String abc;
        @jakarta.xml.bind.annotation.XmlAttribute public String ab;
    }

    @Test
    void decimalBoundariesSurviveTheSingleRangeCheck() {
        // The digit loop multiplies without a per-digit overflow guard; these pin the one final check.
        assertEquals(Integer.MAX_VALUE, bind("<r><n>2147483647</n></r>", Nums.class).n);
        assertEquals(Integer.MIN_VALUE, bind("<r><n>-2147483648</n></r>", Nums.class).n);
        assertEquals(0, bind("<r><n>0</n></r>", Nums.class).n);
        assertEquals(0, bind("<r><n>-0</n></r>", Nums.class).n);
        assertEquals(5, bind("<r><n>0000000000000000005</n></r>", Nums.class).n);
        assertThrows(RuntimeException.class, () -> bind("<r><n>2147483648</n></r>", Nums.class));
        assertThrows(RuntimeException.class, () -> bind("<r><n>-2147483649</n></r>", Nums.class));
        assertThrows(RuntimeException.class, () -> bind("<r><n>99999999999999999999</n></r>", Nums.class));
        assertThrows(RuntimeException.class, () -> bind("<r><n>12a4</n></r>", Nums.class));
    }

    @Test
    void longBoundariesSurviveTheSingleRangeCheck() {
        assertEquals(Long.MAX_VALUE, bind("<r><n>9223372036854775807</n></r>", Longs.class).n);
        assertEquals(Long.MIN_VALUE, bind("<r><n>-9223372036854775808</n></r>", Longs.class).n);
        assertEquals(5L, bind("<r><n>00000000000000000000005</n></r>", Longs.class).n);
        assertEquals(-42L, bind("<r><n> -42 </n></r>", Longs.class).n);
        assertThrows(RuntimeException.class, () -> bind("<r><n>9223372036854775808</n></r>", Longs.class));
    }

    @Test
    void decimalBoundariesAreIdenticalOnTheEntityDecodingPath() {
        // &#50; is '2': this forces the general decode path instead of the raw-byte fast path.
        assertEquals(Integer.MAX_VALUE, bind("<r><n>&#50;147483647</n></r>", Nums.class).n);
        assertEquals(5, bind("<r><n>&#48;000000000000000005</n></r>", Nums.class).n);
        assertThrows(RuntimeException.class, () -> bind("<r><n>&#50;147483648</n></r>", Nums.class));
    }

    @Test
    void decimalBoundariesAreIdenticalOnTheAttributePath() {
        assertEquals(Integer.MAX_VALUE, bind("<r n='2147483647'/>", NumAttr.class).n);
        assertEquals(5, bind("<r n='0000000000000000005'/>", NumAttr.class).n);
        assertThrows(RuntimeException.class, () -> bind("<r n='2147483648'/>", NumAttr.class));
    }

    @Test
    void numericListsUseTheSameConversion() {
        NumList parsed = bind("<r><n>2147483647</n><n>-2147483648</n><n>0000000000000000005</n></r>", NumList.class);
        assertEquals(3, parsed.n.size());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), parsed.n.get(0));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), parsed.n.get(1));
        assertEquals(Integer.valueOf(5), parsed.n.get(2));
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Nums { @jakarta.xml.bind.annotation.XmlElement public int n; }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Nil {
        @jakarta.xml.bind.annotation.XmlElement(nillable = true) public Integer n;
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Kids {
        @jakarta.xml.bind.annotation.XmlElement(name = "child") public List<String> child = new ArrayList<String>();
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Entities { @jakarta.xml.bind.annotation.XmlElement public String text; }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Longs { @jakarta.xml.bind.annotation.XmlElement public long n; }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class NumAttr { @jakarta.xml.bind.annotation.XmlAttribute public int n; }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class NumList {
        @jakarta.xml.bind.annotation.XmlElement(name = "n") public List<Integer> n = new ArrayList<Integer>();
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "p")
    public static class Getters {
        private String a;
        public String getA() { return a; }
        public void setA(String value) { a = value; }
    }
}
