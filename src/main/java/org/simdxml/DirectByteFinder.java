package org.simdxml;

import java.lang.foreign.MemorySegment;

interface DirectByteFinder {
    long find(MemorySegment segment, long from, long to, byte target);
    String name();
}
