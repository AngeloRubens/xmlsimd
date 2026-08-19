package org.simdxml;

import java.util.Locale;

/** Isolates enum-switch versus primitive-int dispatch; run each mode in a separate JVM. */
public final class DispatchStrategyBenchmark {
    private enum Token { UNKNOWN, MESSAGE_ID, TRANSACTION_COUNT, CONTROL_SUM, IBAN, BIC }
    private static final int SIZE = 1 << 12;
    private final int[] ids = new int[SIZE];
    private final Token[] tokens = new Token[SIZE];

    private DispatchStrategyBenchmark() {
        int state = 0x1bca11;
        Token[] values = Token.values();
        for (int i = 0; i < SIZE; i++) {
            state ^= state << 13; state ^= state >>> 17; state ^= state << 5;
            int id = (state & 0x7fffffff) % values.length;
            ids[i] = id; tokens[i] = values[id];
        }
    }
    public static void main(String[] args) {
        if (args.length != 1 || !(args[0].equals("int") || args[0].equals("enum")))
            throw new IllegalArgumentException("Usage: DispatchStrategyBenchmark <int|enum>");
        DispatchStrategyBenchmark benchmark = new DispatchStrategyBenchmark();
        for (int i = 0; i < 10_000; i++) benchmark.run(args[0]);
        int iterations = 50_000;
        long checksum = 0, start = System.nanoTime();
        for (int i = 0; i < iterations; i++)
            checksum = Long.rotateLeft(checksum, 7) ^ benchmark.run(args[0]) ^ i;
        long elapsed = System.nanoTime() - start;
        double dispatches = (double) iterations * SIZE;
        System.out.printf(Locale.ROOT, "dispatch=%s ns/dispatch=%.4f billion/s=%.3f checksum=%d%n",
                args[0], elapsed / dispatches, dispatches / elapsed, checksum);
    }
    private long run(String mode) { return mode.equals("int") ? runInt() : runEnum(); }
    private long runInt() {
        long sum = 0;
        for (int id : ids) sum += switch (id) {
            case 1 -> 11; case 2 -> 23; case 3 -> 37; case 4 -> 53; case 5 -> 71; default -> 3;
        };
        return sum;
    }
    private long runEnum() {
        long sum = 0;
        for (Token token : tokens) sum += switch (token) {
            case MESSAGE_ID -> 11; case TRANSACTION_COUNT -> 23; case CONTROL_SUM -> 37;
            case IBAN -> 53; case BIC -> 71; case UNKNOWN -> 3;
        };
        return sum;
    }
}
