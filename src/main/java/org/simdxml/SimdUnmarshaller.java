package org.simdxml;

import java.util.Set;

/** Reusable JAXB-style unmarshaller; intentionally not thread safe, like Jakarta Unmarshaller. */
public final class SimdUnmarshaller {
    private final SimdXmlParser parser;
    private final Set<Class<?>> boundTypes;
    private final java.util.Map<XmlExpandedName, Class<?>> roots;
    private final java.util.Map<Class<?>, XmlBindingAdapter> adapters = new java.util.HashMap<Class<?>, XmlBindingAdapter>();
    private final XmlBinder binder = new XmlBinder(adapters);
    SimdUnmarshaller(SimdXmlParser parser, Set<Class<?>> boundTypes, java.util.Map<XmlExpandedName, Class<?>> roots) {
        this.parser = parser; this.boundTypes = boundTypes; this.roots = roots;
        java.util.Set<Class<?>> visited = new java.util.HashSet<Class<?>>();
        for (Class<?> type : boundTypes) XmlBindingMetadata.installDefaultAdapters(type, adapters, visited);
    }
    public Object unmarshal(byte[] xml) { return binder.bindAny(parser.reusableStream(xml), roots); }
    public Object unmarshal(javax.xml.stream.XMLStreamReader reader) { return binder.bindAny(StaxBindingReaders.cursor(reader), roots); }
    public Object unmarshal(javax.xml.stream.XMLEventReader reader) { return binder.bindAny(StaxBindingReaders.events(reader), roots); }
    public SimdUnmarshaller withAdapter(Class<?> adapterType, XmlBindingAdapter adapter) {
        adapters.put(java.util.Objects.requireNonNull(adapterType), java.util.Objects.requireNonNull(adapter));
        return this;
    }
    public XmlBindingAdapter adapter(Class<?> adapterType) { return adapters.get(adapterType); }
    /** Creates a reusable push SAX binder sharing this unmarshaller's precompiled adapters. */
    public org.xml.sax.ContentHandler saxHandler() { return new SaxObjectBinderHandler(roots, adapters); }
    public Object saxResult(org.xml.sax.ContentHandler handler) {
        if (!(handler instanceof SaxObjectBinderHandler)) throw new IllegalArgumentException("Not a simdxml SAX handler");
        return ((SaxObjectBinderHandler) handler).result();
    }
    public <T> T unmarshal(byte[] xml, Class<T> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
        return binder.bind(parser.reusableStream(xml), type);
    }
    public <T> T unmarshal(javax.xml.stream.XMLStreamReader reader, Class<T> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
        return binder.bind(StaxBindingReaders.cursor(reader), type);
    }
    public <T> T unmarshal(javax.xml.stream.XMLEventReader reader, Class<T> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
        return binder.bind(StaxBindingReaders.events(reader), type);
    }
    public <T> T unmarshal(byte[] xml, int length, Class<T> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
        return binder.bind(parser.reusableStream(xml, length), type);
    }
}
