package org.simdxml;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Immutable, pre-resolved binding dispatch plan reused for every document of a Java type. */
final class BindingPlan {
    final List<XmlBindingMetadata.Property> properties;
    final List<XmlBindingMetadata.Property> attributes;
    final XmlBindingMetadata.Property value;
    final Map<String, XmlBindingMetadata.Property> localChildren;
    final Map<XmlExpandedName, XmlBindingMetadata.Property> children;
    final Map<XmlExpandedName, XmlBindingMetadata.Property> wrappers;
    /** Resolved once per type: never look this up per bean instance. */
    final GeneratedBeanAccess generated;
    private final XmlBindingMetadata.Property[][] localHash;
    private final int localHashMask;
    private final boolean hashDispatch;
    private final XmlBindingMetadata.Property[][] attrLocalHash;
    private final int attrLocalHashMask;
    private final boolean attrHashDispatch;
    /** Name-keyed fallback: a single non-ASCII attribute name must not disable the others. */
    final Map<String, XmlBindingMetadata.Property> localAttributes;
    private final boolean directBeanCapable;

    private static boolean computeDirectBeanCapable(List<XmlBindingMetadata.Property> properties,
            Map<XmlExpandedName, XmlBindingMetadata.Property> wrappers) {
        if (properties.isEmpty() || !wrappers.isEmpty()) return false;
        for (XmlBindingMetadata.Property property : properties) {
            if (!property.expandedName().namespace().isEmpty() || property.wrapperName() != null
                    || property.adapterType() != null || property.defaultValue() != null
                    || property.list() && (property.rawType().isArray()
                        || !property.rawType().isAssignableFrom(ArrayList.class))) return false;
        }
        return true;
    }

    BindingPlan(Class<?> type) {
        properties = XmlBindingMetadata.properties(type);
        attributes = XmlBindingMetadata.attributes(type);
        value = XmlBindingMetadata.valueProperty(type);
        localChildren = XmlBindingMetadata.localChildren(type);
        children = XmlBindingMetadata.children(type);
        wrappers = XmlBindingMetadata.wrappers(type);
        generated = GeneratedAccessFactory.get(type);
        int tableSize = 1;
        while (tableSize < properties.size() * 2) tableSize <<= 1;
        @SuppressWarnings("unchecked") ArrayList<XmlBindingMetadata.Property>[] buckets = new ArrayList[tableSize];
        boolean dispatchable = true;
        for (XmlBindingMetadata.Property property : properties) {
            if (property.attribute() || property.value() || property.wrapperName() != null
                    || !property.expandedName().namespace().isEmpty()) continue;
            // A non-ASCII local name has no byte hash: the whole type must fall back to name lookup.
            if (property.xmlNameHash() == -1 || property.xmlNameBytes() == null) { dispatchable = false; continue; }
            int slot = property.xmlNameHash() & (tableSize - 1);
            ArrayList<XmlBindingMetadata.Property> bucket = buckets[slot];
            if (bucket == null) buckets[slot] = bucket = new ArrayList<XmlBindingMetadata.Property>(1);
            bucket.add(property);
        }
        localHash = new XmlBindingMetadata.Property[tableSize][];
        for (int i = 0; i < tableSize; i++)
            if (buckets[i] != null) localHash[i] = buckets[i].toArray(new XmlBindingMetadata.Property[buckets[i].size()]);
        localHashMask = tableSize - 1;
        hashDispatch = dispatchable;

        // Attribute hash dispatch
        int attrTableSize = 1;
        while (attrTableSize < attributes.size() * 2) attrTableSize <<= 1;
        @SuppressWarnings("unchecked") ArrayList<XmlBindingMetadata.Property>[] attrBuckets = new ArrayList[attrTableSize];
        boolean attrDispatchable = true;
        for (XmlBindingMetadata.Property property : attributes) {
            // A non-ASCII local name has no byte hash: the whole type must fall back to name lookup.
            if (property.xmlNameHash() == -1 || property.xmlNameBytes() == null) { attrDispatchable = false; continue; }
            int slot = property.xmlNameHash() & (attrTableSize - 1);
            ArrayList<XmlBindingMetadata.Property> bucket = attrBuckets[slot];
            if (bucket == null) attrBuckets[slot] = bucket = new ArrayList<XmlBindingMetadata.Property>(1);
            bucket.add(property);
        }
        attrLocalHash = new XmlBindingMetadata.Property[attrTableSize][];
        for (int i = 0; i < attrTableSize; i++)
            if (attrBuckets[i] != null) attrLocalHash[i] = attrBuckets[i].toArray(new XmlBindingMetadata.Property[attrBuckets[i].size()]);
        attrLocalHashMask = attrTableSize - 1;
        attrHashDispatch = attrDispatchable;
        directBeanCapable = computeDirectBeanCapable(properties, wrappers);
        java.util.HashMap<String, XmlBindingMetadata.Property> byName =
                new java.util.HashMap<String, XmlBindingMetadata.Property>(attributes.size() * 2);
        for (XmlBindingMetadata.Property property : attributes)
            if (property.expandedName().namespace().isEmpty()) byName.put(property.xmlName(), property);
        localAttributes = byName;
    }

    /** False when at least one unqualified child name cannot be matched from bytes alone. */
    boolean hashDispatch() { return hashDispatch; }

    /**
     * Everything the direct-bean fast path requires that depends only on the type. The caller adds
     * the one condition that does not — an empty namespace scope. Deriving this per element cost
     * 5.9% of the sampled stacks, for an answer that cannot change between documents.
     */
    boolean directBeanCapable() { return directBeanCapable; }

    XmlBindingMetadata.Property findLocal(BindingCursor reader) {
        int hash = reader.localNameHash();
        XmlBindingMetadata.Property[] bucket = localHash[hash & localHashMask];
        if (bucket == null) return null;
        int length = reader.localNameLength();
        for (XmlBindingMetadata.Property property : bucket)
            if (property.xmlNameHash() == hash && property.xmlNameLength() == length
                    && reader.localNameEqualsBytes(property.xmlNameBytes())) return property;
        return null;
    }

    XmlBindingMetadata.Property findLocal(XmlByteName reader) {
        int hash = reader.localNameHash();
        XmlBindingMetadata.Property[] bucket = localHash[hash & localHashMask];
        if (bucket == null) return null;
        int length = reader.localNameLength();
        for (XmlBindingMetadata.Property property : bucket)
            if (property.xmlNameHash() == hash && property.xmlNameLength() == length
                    && reader.localEqualsAscii(property.xmlNameBytes())) return property;
        return null;
    }

    /** False when at least one attribute name cannot be matched from bytes alone. */
    boolean attributeHashDispatch() { return attrHashDispatch; }

    XmlBindingMetadata.Property findAttribute(XmlByteName reader) {
        if (!attrHashDispatch) return null;
        int hash = reader.localNameHash();
        XmlBindingMetadata.Property[] bucket = attrLocalHash[hash & attrLocalHashMask];
        if (bucket == null) return null;
        int length = reader.localNameLength();
        for (XmlBindingMetadata.Property property : bucket)
            if (property.xmlNameHash() == hash && property.xmlNameLength() == length
                    && reader.localEqualsAscii(property.xmlNameBytes())) return property;
        return null;
    }
}
