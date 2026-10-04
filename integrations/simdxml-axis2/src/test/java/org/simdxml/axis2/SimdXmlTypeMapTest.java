package org.simdxml.axis2;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class SimdXmlTypeMapTest {
    @Test
    void publishesConsistentImmutableSnapshotsAndReplacesBothDirections() {
        SimdXmlTypeMap types = new SimdXmlTypeMap();
        QName first = new QName("urn:test", "first");
        QName second = new QName("urn:test", "second");

        types.addTypeMapping(String.class, first);
        assertSame(String.class, types.getType(first));
        assertEquals(first, types.getQName(String.class));

        types.addTypeMapping(String.class, second);
        assertFalse(types.isQNameSupported(first));
        assertSame(String.class, types.getType(second));

        types.addTypeMapping(Integer.class, second);
        assertFalse(types.isTypeSupported(String.class));
        assertEquals(second, types.getQName(Integer.class));
    }
}
