package org.simdxml;

import java.util.Map;

/** Minimal event contract consumed by the precompiled object binder. */
interface BindingXmlReader {
    XmlEvent next();
    boolean hasNext();
    String name();
    String text();
    Map<String, String> attributes();
    String attribute(String name);
    boolean hasNamespaceDeclarations();
}
