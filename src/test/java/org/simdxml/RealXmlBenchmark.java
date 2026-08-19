package org.simdxml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Simple reproducible whole-file benchmark; JMH integration can consume the same benchmark body. */
public final class RealXmlBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException(
                "Usage: RealXmlBenchmark <xml-file> [iterations] [stream|index]");
        byte[] xml = Files.readAllBytes(Path.of(args[0]));
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        String mode = args.length > 2 ? args[2] : "stream";
        String backend = System.getProperty("org.simdxml.indexer", "auto");
        BenchmarkBody body = mode.equals("index") ? new IndexBody(xml.length) : new StreamBody(xml.length);
        if (!mode.equals("index") && !mode.equals("stream")) throw new IllegalArgumentException("Unknown mode: " + mode);
        int warmups = Integer.getInteger("org.simdxml.benchmark.warmups", 3);
        long checksum = 0;
        for (int i = 0; i < warmups; i++) checksum = Long.rotateLeft(checksum, 7) ^ body.run(xml);
        System.gc();
        long best = Long.MAX_VALUE, total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            checksum = Long.rotateLeft(checksum, 7) ^ body.run(xml);
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed); total += elapsed;
        }
        double mib = xml.length / 1048576.0;
        double averageSeconds = total / 1_000_000_000.0 / iterations;
        double bestSeconds = best / 1_000_000_000.0;
        System.out.printf(Locale.ROOT,
                "mode=%s backend=%s size=%.2f MiB avg=%.2f MiB/s best=%.2f MiB/s checksum=%d%n",
                mode, backend, mib, mib / averageSeconds, mib / bestSeconds, checksum);
    }

    private interface BenchmarkBody { long run(byte[] xml); }

    private static final class IndexBody implements BenchmarkBody {
        private final StructuralIndexer indexer = new StructuralIndexer();
        private final StructuralIndex index;
        private IndexBody(int capacity) { index = new StructuralIndex(capacity); }
        @Override public long run(byte[] xml) {
            indexer.index(xml, xml.length, index);
            return index.checksum();
        }
    }

    private static final class StreamBody implements BenchmarkBody {
        private final SimdXmlParser parser;
        private StreamBody(int capacity) { parser = new SimdXmlParser(capacity, 4096); }
        @Override public long run(byte[] xml) {
            SimdXmlStreamReader reader = parser.stream(xml);
            long checksum = 0;
            while (reader.hasNext()) {
                XmlEvent event = reader.next();
                checksum = checksum * 31 + event.ordinal();
                if (event == XmlEvent.START_ELEMENT || event == XmlEvent.END_ELEMENT) checksum += reader.name().length();
                else if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) checksum += reader.text().length();
            }
            return checksum;
        }
    }

    private RealXmlBenchmark() { }
}
