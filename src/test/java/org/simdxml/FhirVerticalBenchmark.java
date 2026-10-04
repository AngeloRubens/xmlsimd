package org.simdxml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Sequential A/B benchmark for FHIR XML generic, packed-SWAR, fixed and AUTO dispatch paths. */
public final class FhirVerticalBenchmark {
    private interface Body { FhirMessageInfo inspect(byte[] xml); }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: FhirVerticalBenchmark <packed|generic|fixed|auto> <fhir-xml> [iterations]");
        String mode = args[0];
        byte[] xml = Files.readAllBytes(Path.of(args[1]));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200_000;
        final SimdXmlParser parser;
        final Body body;
        if ("packed".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, true); body = parser::inspectFhir;
        } else if ("generic".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, false); body = parser::inspectFhir;
        } else if ("fixed".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.FHIR).build();
            body = input -> (FhirMessageInfo) parser.inspectVertical(input);
        } else if ("auto".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.AUTO).build();
            body = input -> (FhirMessageInfo) parser.inspectVertical(input);
        } else throw new IllegalArgumentException("Unknown mode: " + mode);
        long checksum = 0;
        for (int i = 0; i < 5_000; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT,
                "fhir=%s size=%d bytes documents/s=%.2f MiB/s=%.2f ns/document=%.1f checksum=%d%n",
                args[0], xml.length, iterations / seconds, xml.length * iterations / 1048576.0 / seconds,
                seconds * 1e9 / iterations, checksum);
    }
    private static long checksum(FhirMessageInfo info) {
        long hash = length(info.resourceType());
        hash = hash * 31 + length(info.id());
        return hash * 31 + 1; // Add constant to avoid zero checksum
    }
    private static int length(String value) { return value == null ? 0 : value.length(); }
    private FhirVerticalBenchmark() { }
}