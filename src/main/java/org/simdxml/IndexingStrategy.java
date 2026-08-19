package org.simdxml;

interface IndexingStrategy {
    void index(byte[] input, int length, StructuralIndex output);
    String name();
}
