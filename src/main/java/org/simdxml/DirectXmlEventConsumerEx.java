package org.simdxml;

/** Extended zero-copy callback exposing event-scoped attributes on START_ELEMENT. */
@FunctionalInterface
public interface DirectXmlEventConsumerEx {
    void onEvent(XmlEvent event, DirectXmlByteSlice name, DirectXmlByteSlice text, DirectXmlAttributes attributes);
}
