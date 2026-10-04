package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The byte-level numeric conversions must produce bit-identical results to the String path they
 * bypass. {@code double} is the delicate one: the fast path is only taken for forms it can round
 * correctly, so this sweeps the boundaries of that condition and falls back everywhere else.
 */
class DecimalConversionTest {

    private static Doubles bindDouble(String lexical) {
        String xml = "<r><d>" + lexical + "</d><f>" + lexical + "</f></r>";
        return SimdJaxbContext.builder(Doubles.class).build().createUnmarshaller()
                .unmarshal(xml.getBytes(StandardCharsets.UTF_8), Doubles.class);
    }

    private static void assertMatchesJdk(String lexical) {
        double expected = Double.parseDouble(lexical.trim());
        Doubles bound = bindDouble(lexical);
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(bound.d),
                () -> "double mismatch for '" + lexical + "'");
        assertEquals(Float.floatToIntBits((float) expected), Float.floatToIntBits(bound.f),
                () -> "float mismatch for '" + lexical + "'");
    }

    @Test
    void simpleDecimalsMatchTheJdkParserBitForBit() {
        String[] cases = {
            "0", "-0", "1", "-1", "0.5", "-0.5", "3.14159", "2.718281828459045",
            "0.1", "0.2", "0.3", "100.0", "1000000", "0.000001",
            "9007199254740992", "9007199254740991", "-9007199254740992",
            "123456789.123456", "0.0000000000000000001", ".5", "-.5", "5.",
            " 42.5 ", "\t-7.25\n", "000000000000000001", "1.000000000000000",
        };
        for (String lexical : cases) assertMatchesJdk(lexical);
    }

    @Test
    void formsOutsideTheFastPathStillGoThroughTheJdkParser() {
        // Exponent notation, the XML infinities and NaN are never taken by the byte path.
        String[] cases = {"1e10", "1E-10", "1.5e3", "-2.5E-7", "INF", "-INF", "NaN",
            "12345678901234567890", "0.12345678901234567890123"};
        for (String lexical : cases) {
            Doubles bound = bindDouble(lexical);
            double expected = expectedXmlDouble(lexical);
            assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(bound.d),
                    () -> "double mismatch for '" + lexical + "'");
        }
    }

    private static double expectedXmlDouble(String lexical) {
        if (lexical.equals("INF")) return Double.POSITIVE_INFINITY;
        if (lexical.equals("-INF")) return Double.NEGATIVE_INFINITY;
        return Double.parseDouble(lexical);
    }

    @Test
    void randomDecimalsMatchTheJdkParserBitForBit() {
        Random random = new Random(20260827L);
        for (int trial = 0; trial < 4000; trial++) {
            StringBuilder text = new StringBuilder();
            if (random.nextBoolean()) text.append('-');
            int intDigits = random.nextInt(19);
            for (int i = 0; i < intDigits; i++) text.append((char) ('0' + random.nextInt(10)));
            if (intDigits == 0) text.append('0');
            if (random.nextBoolean()) {
                text.append('.');
                int fracDigits = random.nextInt(22);
                for (int i = 0; i < fracDigits; i++) text.append((char) ('0' + random.nextInt(10)));
            }
            assertMatchesJdk(text.toString());
        }
    }

    @Test
    void malformedDecimalsAreRejected() {
        for (String lexical : new String[] {"1.2.3", "abc", "1x", "--1", "1-2"})
            assertThrows(RuntimeException.class, () -> bindDouble(lexical), lexical);
    }

    @Test
    void booleansConvertFromBytesOnBothReaders() {
        assertTrue(bindBoolean("true").b);
        assertTrue(bindBoolean("1").b);
        assertTrue(bindBoolean(" true ").b);
        org.junit.jupiter.api.Assertions.assertFalse(bindBoolean("false").b);
        org.junit.jupiter.api.Assertions.assertFalse(bindBoolean("0").b);
        org.junit.jupiter.api.Assertions.assertFalse(bindBoolean(" false\n").b);
        assertThrows(RuntimeException.class, () -> bindBoolean("yes"));
        assertThrows(RuntimeException.class, () -> bindBoolean(""));
    }

    private static Bools bindBoolean(String lexical) {
        String xml = "<r><b>" + lexical + "</b></r>";
        return SimdJaxbContext.builder(Bools.class).build().createUnmarshaller()
                .unmarshal(xml.getBytes(StandardCharsets.UTF_8), Bools.class);
    }

    @Test
    void theDirectBinderAgreesWithTheStandardBinderOnDecimals() {
        Random random = new Random(4242L);
        DirectSimdXmlParser parser = DirectSimdXmlParser.builder().build();
        for (int trial = 0; trial < 500; trial++) {
            String lexical = (random.nextBoolean() ? "-" : "") + random.nextInt(1000000)
                    + "." + random.nextInt(1000000);
            String xml = "<r><d>" + lexical + "</d><f>" + lexical + "</f></r>";
            Doubles standard = SimdJaxbContext.builder(Doubles.class).build().createUnmarshaller()
                    .unmarshal(xml.getBytes(StandardCharsets.UTF_8), Doubles.class);
            Doubles direct = parser.bind(
                    java.lang.foreign.MemorySegment.ofArray(xml.getBytes(StandardCharsets.UTF_8)), Doubles.class);
            assertEquals(Double.doubleToLongBits(standard.d), Double.doubleToLongBits(direct.d), lexical);
            assertEquals(Float.floatToIntBits(standard.f), Float.floatToIntBits(direct.f), lexical);
            assertEquals(Double.doubleToLongBits(Double.parseDouble(lexical)),
                    Double.doubleToLongBits(direct.d), lexical);
        }
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Doubles {
        @jakarta.xml.bind.annotation.XmlElement public double d;
        @jakarta.xml.bind.annotation.XmlElement public float f;
    }

    @jakarta.xml.bind.annotation.XmlRootElement(name = "r")
    @jakarta.xml.bind.annotation.XmlAccessorType(jakarta.xml.bind.annotation.XmlAccessType.FIELD)
    public static class Bools {
        @jakarta.xml.bind.annotation.XmlElement public boolean b;
    }
}
