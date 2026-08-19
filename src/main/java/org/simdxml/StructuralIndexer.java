package org.simdxml;

/** Selects the fastest stage-1 implementation that the current runtime can load. */
final class StructuralIndexer {
    private final IndexingStrategy strategy = select();

    void index(byte[] input, int length, StructuralIndex output) { strategy.index(input, length, output); }
    String strategyName() { return strategy.name(); }

    private static IndexingStrategy select() {
        String requested = System.getProperty("org.simdxml.indexer", "auto");
        if (requested.equals("baseline")) return new BaselineStructuralIndexer();
        if (requested.equals("scalar") || requested.equals("swar")) return new ScalarStructuralIndexer();
        if (requested.equals("varhandle")) return load("org.simdxml.VarHandleStructuralIndexer", "VarHandle");
        if (requested.equals("vector")) return load("org.simdxml.VectorStructuralIndexer", "Vector");
        if (requested.equals("unsafe")) return load("org.simdxml.UnsafeStructuralIndexer", "Unsafe");
        if (!requested.equals("auto")) throw new IllegalArgumentException("Unknown indexer: " + requested);
        IndexingStrategy vector = tryLoad("org.simdxml.VectorStructuralIndexer");
        if (vector != null) return vector;
        IndexingStrategy unsafe = tryLoad("org.simdxml.UnsafeStructuralIndexer");
        if (unsafe != null) return unsafe;
        return new ScalarStructuralIndexer();
    }

    private static IndexingStrategy load(String className, String label) {
        IndexingStrategy strategy = tryLoad(className);
        if (strategy == null) throw new IllegalStateException(label + " backend requested but unavailable");
        return strategy;
    }
    private static IndexingStrategy tryLoad(String className) {
        try {
            return (IndexingStrategy) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return null;
        }
    }
}
