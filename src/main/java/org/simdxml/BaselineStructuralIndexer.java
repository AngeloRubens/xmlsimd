package org.simdxml;

/** Deliberately simple byte-at-a-time reference used to verify that optimized backends pay off. */
final class BaselineStructuralIndexer implements IndexingStrategy {
    private static final boolean[] STRUCTURAL = createLookup();

    @Override public void index(byte[] input, int length, StructuralIndex output) {
        output.clear();
        int base = 0;
        while (base < length) {
            int blockLength = Math.min(64, length - base);
            long mask = 0;
            for (int lane = 0; lane < blockLength; lane++)
                if (STRUCTURAL[input[base + lane] & 0xff]) mask |= 1L << lane;
            output.addMask(base, mask);
            base += blockLength;
        }
    }

    @Override public String name() { return "baseline-byte-loop"; }

    private static boolean[] createLookup() {
        boolean[] table = new boolean[256];
        table['<'] = table['>'] = table['/'] = table['='] = true;
        table['\''] = table['"'] = table['&'] = table['?'] = table['!'] = true;
        return table;
    }
}
