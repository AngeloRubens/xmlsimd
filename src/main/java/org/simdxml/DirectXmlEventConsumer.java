package org.simdxml;

/** Name is present for element/PI events; text is present for text, CDATA, comment, and PI events. */
@FunctionalInterface
public interface DirectXmlEventConsumer {
    void onEvent(XmlEvent event, DirectXmlByteSlice name, DirectXmlByteSlice text);
}
