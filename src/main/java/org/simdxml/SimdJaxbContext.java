package org.simdxml;

import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashSet;

/** Immutable, shareable JAXB-style context with precompiled annotation/constructor metadata. */
public final class SimdJaxbContext {
    private final Set<Class<?>> boundTypes;
    private final int capacity, maxDepth;
    private final boolean vertical;
    private final Utf8Validation utf8;
    private final ThreadSafeSimdXmlParser shared;
    private final Map<XmlExpandedName, Class<?>> roots;
    private final Set<Class<?>> adapterTypes;

    public static Builder builder(Class<?>... boundTypes) { return new Builder(boundTypes); }
    public static SimdJaxbContext newInstance(Class<?>... boundTypes) { return builder(boundTypes).build(); }
    /** Active precompiled field-write backend, useful for A/B benchmarks and diagnostics. */
    public static String bindingAccessStrategy() { return XmlBindingMetadata.accessStrategy(); }

    private SimdJaxbContext(Builder builder) {
        if (builder.types.isEmpty()) throw new IllegalArgumentException("At least one bound type is required");
        boundTypes = Collections.unmodifiableSet(new LinkedHashSet<Class<?>>(builder.types)); capacity = builder.capacity; maxDepth = builder.maxDepth;
        vertical = builder.vertical; utf8 = builder.utf8;
        for (Class<?> type : boundTypes) XmlBindingMetadata.prewarm(type);
        java.util.HashMap<XmlExpandedName, Class<?>> rootMap = new java.util.HashMap<XmlExpandedName, Class<?>>();
        for (Class<?> type : boundTypes) {
            Class<?> previous = rootMap.put(XmlBindingMetadata.rootName(type), type);
            if (previous != null) throw new XmlBindingException("Duplicate bound root name for " + type.getName());
        }
        roots = Collections.unmodifiableMap(rootMap);
        java.util.LinkedHashSet<Class<?>> discoveredAdapters = new java.util.LinkedHashSet<Class<?>>();
        java.util.HashSet<Class<?>> visitedAdapters = new java.util.HashSet<Class<?>>();
        for (Class<?> type : boundTypes)
            XmlBindingMetadata.collectAdapterTypes(type, discoveredAdapters, visitedAdapters);
        adapterTypes = Collections.unmodifiableSet(discoveredAdapters);
        shared = SimdXmlParser.builder().withCapacity(capacity).withMaxDepth(maxDepth)
                .withVerticalOptimizations(vertical).withUtf8Validation(utf8).buildThreadSafe();
    }

    /** Creates a reusable, non-thread-safe unmarshaller, matching JAXB's lifecycle. */
    public SimdUnmarshaller createUnmarshaller() {
        return new SimdUnmarshaller(SimdXmlParser.builder().withCapacity(capacity).withMaxDepth(maxDepth)
                .withVerticalOptimizations(vertical).withUtf8Validation(utf8).build(), boundTypes, roots);
    }

    /** Creates a reusable, non-thread-safe UTF-8 marshaller. */
    public SimdMarshaller createMarshaller() { return new SimdMarshaller(boundTypes); }
    /** Adapter classes discovered while precompiling JAXB-neutral metadata. */
    public Set<Class<?>> adapterTypes() { return adapterTypes; }

    /** Thread-safe convenience binding using the context's platform/virtual-thread strategy. */
    public <T> T unmarshal(byte[] xml, Class<T> type) {
        requireBound(type); return shared.parse(xml, type);
    }
    /** Resolves a globally bound root and binds it in the same parser pass. */
    public Object unmarshal(byte[] xml) { return shared.parseBound(xml, roots); }
    private void requireBound(Class<?> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
    }

    public static final class Builder {
        private final Set<Class<?>> types;
        private int capacity = 34 * 1024 * 1024, maxDepth = 1024;
        private boolean vertical = true;
        private Utf8Validation utf8 = Utf8Validation.STRICT;
        private Builder(Class<?>... types) {
            LinkedHashSet<Class<?>> copy = new LinkedHashSet<Class<?>>();
            Collections.addAll(copy, types.clone());
            this.types = Collections.unmodifiableSet(copy);
        }
        public Builder withCapacity(int value) { if (value < 1) throw new IllegalArgumentException(); capacity = value; return this; }
        public Builder withMaxDepth(int value) { if (value < 1) throw new IllegalArgumentException(); maxDepth = value; return this; }
        public Builder withVerticalOptimizations(boolean value) { vertical = value; return this; }
        public Builder withUtf8Validation(Utf8Validation value) { utf8 = java.util.Objects.requireNonNull(value); return this; }
        public SimdJaxbContext build() { return new SimdJaxbContext(this); }
    }
}
