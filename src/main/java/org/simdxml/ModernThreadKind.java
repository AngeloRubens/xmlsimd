package org.simdxml;

/** JDK 21+ provider; packaged separately from the Java 8 baseline in the compatibility build. */
final class ModernThreadKind implements ThreadKind {
    @Override public boolean isVirtual(Thread thread) { return thread.isVirtual(); }
}
