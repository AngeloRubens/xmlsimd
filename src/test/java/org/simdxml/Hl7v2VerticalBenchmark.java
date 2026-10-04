package org.simdxml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Sequential A/B benchmark for HL7 v2.x generic, packed-SWAR, fixed and AUTO dispatch paths. */
public final class Hl7v2VerticalBenchmark {
    private interface Body { Hl7v2MessageInfo inspect(byte[] xml); }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: Hl7v2VerticalBenchmark <packed|generic|fixed|auto> <hl7v2-file> [iterations]");
        String mode = args[0];
        byte[] xml = Files.readAllBytes(Path.of(args[1]));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200_000;
        final SimdXmlParser parser;
        final Body body;
        if ("packed".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, true); body = parser::inspectHl7v2;
        } else if ("generic".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, false); body = parser::inspectHl7v2;
        } else if ("fixed".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.HL7_V2).build();
            body = input -> (Hl7v2MessageInfo) parser.inspectVertical(input);
        } else if ("auto".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.AUTO).build();
            body = input -> (Hl7v2MessageInfo) parser.inspectVertical(input);
        } else throw new IllegalArgumentException("Unknown mode: " + mode);
        long checksum = 0;
        for (int i = 0; i < 5_000; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT,
                "hl7v2=%s size=%d bytes documents/s=%.2f MiB/s=%.2f ns/document=%.1f checksum=%d%n",
                args[0], xml.length, iterations / seconds, xml.length * iterations / 1048576.0 / seconds,
                seconds * 1e9 / iterations, checksum);
    }
    private static long checksum(Hl7v2MessageInfo info) {
        long hash = length(info.sendingApplication());
        hash = hash * 31 + length(info.sendingFacility());
        hash = hash * 31 + length(info.receivingApplication());
        hash = hash * 31 + length(info.receivingFacility());
        hash = hash * 31 + length(info.dateTimeOfMessage());
        hash = hash * 31 + length(info.messageType());
        hash = hash * 31 + length(info.messageControlId());
        hash = hash * 31 + length(info.processingId());
        return hash * 31 + length(info.versionId());
    }
    private static int length(String value) { return value == null ? 0 : value.length(); }
    private Hl7v2VerticalBenchmark() { }
}