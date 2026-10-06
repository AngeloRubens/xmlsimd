package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XmlGrammarScanStudyTest {
    @Test
    void swarMatchesScalarAcrossXmlContextsAndBlockAlignments() {
        String[] documents = {
                "<?xml version='1.0'?><r a=\"x > &amp; \"/><n>text &amp; more</n></r>",
                "<r><!-- <fake a='b'> &amp; --><x><![CDATA[<fake> & raw]]></x></r>",
                "<!DOCTYPE r [<!ELEMENT r (#PCDATA)><!ENTITY e 'value > x'>]><r>&e;</r>",
                "<root>ASCII café 東京 🧪</root>"
        };
        for (String document : documents) {
            byte[] encoded = document.getBytes(StandardCharsets.UTF_8);
            byte[] input = new byte[encoded.length + 24];
            System.arraycopy(encoded, 0, input, 11, encoded.length);
            for (int prefix = 0; prefix < 16; prefix++) {
                assertEquals(XmlGrammarScanBenchmark.scanScalar(input, 11 + prefix, encoded.length - prefix),
                        XmlGrammarScanBenchmark.scanSwar(input, 11 + prefix, encoded.length - prefix),
                        "prefix=" + prefix + ", document=" + document);
            }
        }
    }

    @Test
    void handlesShortAndEmptyRanges() {
        for (String document : new String[]{"", "<", "&", "é", "<!--x-->"}) {
            byte[] input = document.getBytes(StandardCharsets.UTF_8);
            assertEquals(XmlGrammarScanBenchmark.scanScalar(input, 0, input.length),
                    XmlGrammarScanBenchmark.scanSwar(input, 0, input.length), document);
        }
    }
}
