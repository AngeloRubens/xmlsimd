package org.simdxml;

/** Callback used by the allocation-bounded reusable scan API. */
@FunctionalInterface
public interface XmlEventConsumer {
    void onEvent(XmlEvent event, SimdXmlStreamReader reader);
}
