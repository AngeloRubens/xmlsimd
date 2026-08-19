package org.simdxml;

/** Runtime-selected thread classifier; the Java 8 baseline has no link to virtual-thread APIs. */
interface ThreadKind {
    boolean isVirtual(Thread thread);
}
