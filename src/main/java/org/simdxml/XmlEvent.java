package org.simdxml;

/** Events exposed by {@link SimdXmlStreamReader}. */
public enum XmlEvent {
    START_DOCUMENT, START_ELEMENT, TEXT, CDATA, COMMENT,
    PROCESSING_INSTRUCTION, END_ELEMENT, END_DOCUMENT
}
