package org.simdxml;

/** A node in the compact XML DOM. */
public interface XmlNode {
    enum Type { ELEMENT, TEXT, CDATA, COMMENT, PROCESSING_INSTRUCTION }
    Type type();
}
