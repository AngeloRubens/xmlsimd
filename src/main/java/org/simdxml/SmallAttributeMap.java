package org.simdxml;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable-to-callers small map: the common 1-4 XML attributes need no table or node objects. */
final class SmallAttributeMap extends AbstractMap<String, String> {
    private String k0, v0, k1, v1, k2, v2, k3, v3;
    /* attributes() is public API and reported in document order, so the overflow keeps insertion order. */
    private LinkedHashMap<String, String> overflow;
    private int size;


    boolean putUnique(String key, String value) {
        if (get(key) != null) return false;
        switch (size) {
            case 0: k0 = key; v0 = value; break;
            case 1: k1 = key; v1 = value; break;
            case 2: k2 = key; v2 = value; break;
            case 3: k3 = key; v3 = value; break;
            default:
                if (overflow == null) overflow = new LinkedHashMap<>();
                overflow.put(key, value);
                break;
        }
        size++;
        return true;
    }

    @Override public String get(Object key) {
        if (key == null) return null;
        if (key.equals(k0)) return v0;
        if (key.equals(k1)) return v1;
        if (key.equals(k2)) return v2;
        if (key.equals(k3)) return v3;
        return overflow == null ? null : overflow.get(key);
    }
    @Override public int size() { return size; }
    @Override public Set<Entry<String, String>> entrySet() {
        LinkedHashSet<Entry<String, String>> entries = new LinkedHashSet<>(size * 2);
        if (size > 0) entries.add(new SimpleImmutableEntry<String, String>(k0, v0));
        if (size > 1) entries.add(new SimpleImmutableEntry<String, String>(k1, v1));
        if (size > 2) entries.add(new SimpleImmutableEntry<String, String>(k2, v2));
        if (size > 3) entries.add(new SimpleImmutableEntry<String, String>(k3, v3));
        if (overflow != null) entries.addAll(overflow.entrySet());
        return java.util.Collections.unmodifiableSet(entries);
    }
}
