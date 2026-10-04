package org.simdxml.axis2;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Bidirectional type registry used by the Axis2 bridge. */
public final class SimdXmlTypeMap {
    private final AtomicReference<Snapshot> state = new AtomicReference<Snapshot>(Snapshot.EMPTY);

    /** Registration is a cold-path copy-on-write operation; all runtime lookups are lock-free. */
    public void addTypeMapping(Class<?> type, QName name) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(name, "name");
        Snapshot current;
        Snapshot updated;
        do {
            current = state.get();
            Map<QName, Class<?>> byName = new HashMap<QName, Class<?>>(current.byName);
            Map<Class<?>, QName> byType = new HashMap<Class<?>, QName>(current.byType);
            QName oldName = byType.put(type, name);
            if (oldName != null) byName.remove(oldName);
            Class<?> oldType = byName.put(name, type);
            if (oldType != null && oldType != type) byType.remove(oldType);
            updated = new Snapshot(byName, byType);
        } while (!state.compareAndSet(current, updated));
    }

    public Class<?> getType(QName name) { return state.get().byName.get(name); }
    public QName getQName(Class<?> type) { return state.get().byType.get(type); }
    public boolean isQNameSupported(QName name) { return state.get().byName.containsKey(name); }
    public boolean isTypeSupported(Class<?> type) { return state.get().byType.containsKey(type); }
    public Map<QName, Class<?>> mappings() {
        return state.get().byName;
    }

    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Collections.<QName, Class<?>>emptyMap(),
                Collections.<Class<?>, QName>emptyMap());
        final Map<QName, Class<?>> byName;
        final Map<Class<?>, QName> byType;

        Snapshot(Map<QName, Class<?>> byName, Map<Class<?>, QName> byType) {
            this.byName = Collections.unmodifiableMap(byName);
            this.byType = Collections.unmodifiableMap(byType);
        }
    }
}
