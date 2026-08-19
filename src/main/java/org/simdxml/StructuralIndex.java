package org.simdxml;

import java.util.Arrays;

final class StructuralIndex {
    private int[] values;
    private int size;
    private boolean directSearch;
    /* Stage 2 probes only markup and entity delimiters: keep its hot state in one cache line. */
    private int markupCursor;
    private int entityCursor;

    /* XML text is commonly sparse in structurals; grow on demand instead of reserving 1 int/4 bytes. */
    StructuralIndex(int capacity) {
        values = new int[Math.max(16, Math.min(4096, Math.min(capacity, capacity / 32 + 1)))];
    }
    void clear() { size = markupCursor = entityCursor = 0; directSearch = false; }
    void useDirectSearch() { size = 0; directSearch = true; }
    void addMask(int base, long mask) {
        ensure(size + Long.bitCount(mask));
        while (mask != 0) {
            values[size++] = base + Long.numberOfTrailingZeros(mask);
            mask &= mask - 1;
        }
    }
    int size() { return size; }
    long checksum() {
        long hash = size;
        for (int i = 0; i < size; i++) hash = hash * 0x9e3779b97f4a7c15L + values[i];
        return hash;
    }
    boolean sameValues(StructuralIndex other) {
        if (size != other.size) return false;
        for (int i = 0; i < size; i++) if (values[i] != other.values[i]) return false;
        return true;
    }
    int next(int offset, byte token, byte[] input) {
        return next(offset, input.length, token, input);
    }

    int next(int offset, int limit, byte token, byte[] input) {
        if (directSearch) {
            for (int i = offset; i < limit; i++) if (input[i] == token) return i;
            return input.length;
        }
        boolean entity = token == '&';
        int pos = entity ? entityCursor : markupCursor;
        while (pos < size && values[pos] < offset) pos++;
        while (pos < size) {
            int candidate = values[pos];
            if (candidate >= limit) {
                if (entity) entityCursor = pos; else markupCursor = pos;
                return input.length;
            }
            pos++;
            if (input[candidate] == token) {
                if (entity) entityCursor = pos; else markupCursor = pos;
                return candidate;
            }
        }
        if (entity) entityCursor = pos; else markupCursor = pos;
        return input.length;
    }
    private void ensure(int needed) {
        if (needed > values.length) values = Arrays.copyOf(values, Math.max(needed, values.length * 2));
    }
}
