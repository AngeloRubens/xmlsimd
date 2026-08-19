package org.simdxml;

final class HeapLongAccesses {
    static final HeapLongAccess FASTEST = load();
    private static HeapLongAccess load() {
        try {
            return (HeapLongAccess) Class.forName("org.simdxml.VarHandleLongAccess").getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException unavailable) {
            return portable();
        } catch (LinkageError unavailable) {
            return portable();
        }
    }
    private static HeapLongAccess portable() {
        return new HeapLongAccess() {
            @Override public long littleEndian(byte[] input, int offset) { return PortableLongs.littleEndian(input, offset); }
        };
    }
    private HeapLongAccesses() { }
}
