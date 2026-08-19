package org.simdxml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Sequential A/B benchmark for ISO 20022 generic, packed-SWAR, fixed and AUTO dispatch paths. */
public final class PaymentVerticalBenchmark {
    private interface Body { PaymentMessageInfo inspect(byte[] xml); }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException(
                "Usage: PaymentVerticalBenchmark <packed|generic|fixed|auto> <payment-xml> [iterations]");
        String mode = args[0];
        byte[] xml = Files.readAllBytes(Path.of(args[1]));
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200_000;
        final SimdXmlParser parser;
        final Body body;
        if ("packed".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, true); body = parser::inspectPayment;
        } else if ("generic".equals(mode)) {
            parser = new SimdXmlParser(Math.max(1, xml.length), 128, false); body = parser::inspectPayment;
        } else if ("fixed".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.PAYMENTS).build();
            body = input -> (PaymentMessageInfo) parser.inspectVertical(input);
        } else if ("auto".equals(mode)) {
            parser = SimdXmlParser.builder().withCapacity(Math.max(1, xml.length)).withMaxDepth(128)
                    .withVerticalProfile(VerticalProfile.AUTO).build();
            body = input -> (PaymentMessageInfo) parser.inspectVertical(input);
        } else throw new IllegalArgumentException("Unknown mode: " + mode);
        long checksum = 0;
        for (int i = 0; i < 5_000; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) checksum = Long.rotateLeft(checksum, 7) ^ checksum(body.inspect(xml));
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.ROOT,
                "payment=%s size=%d bytes documents/s=%.2f MiB/s=%.2f ns/document=%.1f checksum=%d%n",
                args[0], xml.length, iterations / seconds, xml.length * iterations / 1048576.0 / seconds,
                seconds * 1e9 / iterations, checksum);
    }
    private static long checksum(PaymentMessageInfo info) {
        long hash = info.protocol().ordinal() * 31L + info.transactionCount();
        hash = hash * 31 + length(info.messageType()); hash = hash * 31 + length(info.messageId());
        hash = hash * 31 + length(info.controlSum()); hash = hash * 31 + length(info.firstIban());
        return hash * 31 + length(info.firstBic());
    }
    private static int length(String value) { return value == null ? 0 : value.length(); }
    private PaymentVerticalBenchmark() { }
}
