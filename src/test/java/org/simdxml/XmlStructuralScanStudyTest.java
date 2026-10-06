package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlStructuralScanStudyTest {
    @Test
    void swarMatchesScalarForAsciiMixedAndUnicodeXmlAtEveryAlignment() {
        String[] documents = {
                "<root a=\"x\"><child>plain text</child></root>",
                "<feed><entry>café 東京</entry><entry>&amp; naïve</entry></feed>",
                "<root><![CDATA[🌍 & <text>]]><x attr='quote'/></root>"
        };
        for (String document : documents) {
            byte[] encoded = document.getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[encoded.length + 15];
            System.arraycopy(encoded, 0, input, 7, encoded.length);
            for (int offset = 0; offset <= 7; offset++) {
                assertEquals(
                        XmlStructuralScanBenchmark.scanScalar(input, 7 + offset, encoded.length - offset),
                        XmlStructuralScanBenchmark.scanSwar(input, 7 + offset, encoded.length - offset),
                        "offset=" + offset + ", document=" + document);
            }
        }
    }

    @Test
    void swarMatchesScalarForEmptyAndShortInputs() {
        for (String value : new String[]{"", "<", "<x>", "é", "&amp;"}) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            assertEquals(XmlStructuralScanBenchmark.scanScalar(bytes, 0, bytes.length),
                    XmlStructuralScanBenchmark.scanSwar(bytes, 0, bytes.length), value);
        }
    }
}
