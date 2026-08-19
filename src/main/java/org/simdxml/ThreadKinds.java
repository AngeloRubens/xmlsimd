package org.simdxml;

final class ThreadKinds {
    private static final ThreadKind CURRENT = load();
    static boolean isVirtual(Thread thread) { return CURRENT.isVirtual(thread); }
    private static ThreadKind load() {
        try {
            return (ThreadKind) Class.forName("org.simdxml.ModernThreadKind").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException unavailable) {
            return new ThreadKind() { @Override public boolean isVirtual(Thread thread) { return false; } };
        } catch (LinkageError unavailable) {
            return new ThreadKind() { @Override public boolean isVirtual(Thread thread) { return false; } };
        }
    }
    private ThreadKinds() { }
}
