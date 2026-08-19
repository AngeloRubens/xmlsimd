package org.simdxml;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Per-document XML symbol table keyed directly by UTF-8 slices. */
final class ByteNameCache {
    private static final int MAX_PERSISTENT_NAMES = 1024;
    private final boolean persistent;
    private int[] hashes = new int[64];
    private int[] starts = new int[64];
    private int[] lengths = new int[64];
    private String[] names = new String[64];
    private byte[][] keys;
    private int size;

    ByteNameCache() { this(false); }
    ByteNameCache(boolean persistent) {
        this.persistent = persistent;
        if (persistent) keys = new byte[64][];
    }

    void clear() {
        if (persistent) return;
        Arrays.fill(names, null);
        size = 0;
    }

    String intern(byte[] input, int start, int end, int hash) {
        if (persistent && size >= MAX_PERSISTENT_NAMES) resetPersistent();
        if ((size + 1) * 2 > names.length) resize(input);
        int length = end - start;
        int slot = mix(hash) & (names.length - 1);
        while (names[slot] != null) {
            if (hashes[slot] == hash && lengths[slot] == length
                    && (persistent ? equals(keys[slot], 0, input, start, length)
                                   : equals(input, starts[slot], start, length)))
                return names[slot];
            slot = slot + 1 & (names.length - 1);
        }
        String name = new String(input, start, length, StandardCharsets.UTF_8);
        hashes[slot] = hash; starts[slot] = start; lengths[slot] = length; names[slot] = name; size++;
        if (persistent) keys[slot] = Arrays.copyOfRange(input, start, end);
        return name;
    }

    private void resize(byte[] input) {
        int[] oldHashes = hashes, oldStarts = starts, oldLengths = lengths;
        String[] oldNames = names;
        byte[][] oldKeys = keys;
        int capacity = names.length << 1;
        hashes = new int[capacity]; starts = new int[capacity]; lengths = new int[capacity]; names = new String[capacity];
        if (persistent) keys = new byte[capacity][];
        size = 0;
        for (int i = 0; i < oldNames.length; i++) {
            if (oldNames[i] == null) continue;
            int slot = mix(oldHashes[i]) & (capacity - 1);
            while (names[slot] != null) slot = slot + 1 & (capacity - 1);
            hashes[slot] = oldHashes[i]; starts[slot] = oldStarts[i]; lengths[slot] = oldLengths[i];
            names[slot] = oldNames[i]; size++;
            if (persistent) keys[slot] = oldKeys[i];
        }
    }
    private void resetPersistent() {
        Arrays.fill(names, null);
        Arrays.fill(keys, null);
        size = 0;
    }
    private static boolean equals(byte[] input, int left, int right, int length) {
        for (int i = 0; i < length; i++) if (input[left + i] != input[right + i]) return false;
        return true;
    }
    private static boolean equals(byte[] left, int leftOffset, byte[] right, int rightOffset, int length) {
        for (int i = 0; i < length; i++) if (left[leftOffset + i] != right[rightOffset + i]) return false;
        return true;
    }
    private static int mix(int value) {
        value ^= value >>> 16; value *= 0x7feb352d; value ^= value >>> 15; value *= 0x846ca68b; return value ^ value >>> 16;
    }
}
