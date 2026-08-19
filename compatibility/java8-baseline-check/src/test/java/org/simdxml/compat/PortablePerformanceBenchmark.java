package org.simdxml.compat;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.simdxml.SimdXmlParser;
import org.simdxml.SimdXmlStreamReader;
import org.simdxml.Utf8Validation;
import org.simdxml.XmlEvent;

/** Java-8-bytecode benchmark used to compare the same portable engine across runtimes. */
public final class PortablePerformanceBenchmark {
    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "bytes";
        String validation = args.length > 1 ? args[1] : "strict";
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 5_000;
        byte[] xml = fixture(512);
        Utf8Validation utf8 = "none".equals(validation) ? Utf8Validation.NONE : Utf8Validation.STRICT;
        SimdXmlParser parser = SimdXmlParser.builder().withCapacity(xml.length).withMaxDepth(64)
                .withUtf8Validation(utf8).build();
        long checksum = 0;
        for (int i = 0; i < 1_000; i++) checksum = Long.rotateLeft(checksum, 7) ^ consume(parser, xml, mode);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 7) ^ consume(parser, xml, mode);
        long elapsed = System.nanoTime() - start;
        double seconds = elapsed / 1e9;
        double mib = ((double) xml.length * iterations) / 1048576.0;
        System.out.printf(Locale.ROOT,
                "portable mode=%s runtime=%s backend=%s utf8=%s utf8Strategy=%s size=%d iterations=%d MiB/s=%.2f documents/s=%.2f ns/document=%.1f checksum=%d%n",
                mode, System.getProperty("java.version"), parser.indexingStrategy(), parser.utf8Validation(),
                parser.utf8ValidationStrategy(), xml.length, iterations, mib / seconds,
                iterations / seconds, (double) elapsed / iterations, checksum);
    }

    private static long consume(SimdXmlParser parser, byte[] xml, String mode) {
        SimdXmlStreamReader reader = parser.reusableStream(xml);
        long hash = 0;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            hash = hash * 31 + event.ordinal();
            if (event == XmlEvent.START_ELEMENT || event == XmlEvent.END_ELEMENT) {
                hash += "bytes".equals(mode) ? reader.nameBytes().length() : reader.name().length();
            } else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) {
                hash += "bytes".equals(mode) ? reader.rawTextBytes().length() : reader.text().length();
            }
        }
        return hash;
    }

    private static byte[] fixture(int entries) {
        StringBuilder xml = new StringBuilder(entries * 96).append("<catalog>");
        for (int i = 0; i < entries; i++) {
            xml.append("<entry id='").append(i).append("'><name>XML-").append(i)
                    .append("</name><amount currency='EUR'>123.45</amount></entry>");
        }
        return xml.append("</catalog>").toString().getBytes(StandardCharsets.UTF_8);
    }

    private PortablePerformanceBenchmark() { }
}
