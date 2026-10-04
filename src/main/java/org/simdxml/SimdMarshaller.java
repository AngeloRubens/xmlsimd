package org.simdxml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Reusable, non-thread-safe JAXB-style UTF-8 marshaller. */
public final class SimdMarshaller {
    private final java.util.Set<Class<?>> boundTypes;
    private final java.util.Map<Class<?>, XmlBindingAdapter> adapters = new java.util.HashMap<Class<?>, XmlBindingAdapter>();
    private final Utf8XmlWriter writer = new Utf8XmlWriter();
    private boolean xmlDeclaration = true;
    private XmlAttachmentHandler attachments;

    SimdMarshaller(java.util.Set<Class<?>> boundTypes) {
        this.boundTypes = boundTypes;
        java.util.Set<Class<?>> visited = new java.util.HashSet<Class<?>>();
        for (Class<?> type : boundTypes) XmlBindingMetadata.installDefaultAdapters(type, adapters, visited);
    }

    /** Controls whether the XML declaration is emitted; JAXB fragment mode disables it. */
    public SimdMarshaller withXmlDeclaration(boolean enabled) { xmlDeclaration = enabled; return this; }
    public SimdMarshaller withAttachmentHandler(XmlAttachmentHandler handler) { attachments = handler; return this; }

    /** Registers an already constructed adapter; registration is outside the marshal loop. */
    public SimdMarshaller withAdapter(Class<?> adapterType, XmlBindingAdapter adapter) {
        adapters.put(java.util.Objects.requireNonNull(adapterType), java.util.Objects.requireNonNull(adapter));
        return this;
    }

    public XmlBindingAdapter adapter(Class<?> adapterType) { return adapters.get(adapterType); }

    public byte[] marshal(Object value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
        marshal(value, output);
        return output.toByteArray();
    }

    public void marshal(Object value, OutputStream output) {
        java.util.Objects.requireNonNull(value, "value");
        java.util.Objects.requireNonNull(output, "output");
        requireBound(value.getClass());
        try {
            writer.reset(output);
            if (xmlDeclaration) writer.raw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writeObject(value, XmlBindingMetadata.rootName(value.getClass()), XmlExpandedName.EMPTY_NAMESPACE);
            writer.finish();
        } catch (IOException e) { throw new XmlBindingException("Cannot write XML", e); }
    }

    /** Emits binding events directly to StAX without an intermediate XML buffer or reparsing. */
    public void marshal(Object value, javax.xml.stream.XMLStreamWriter output) {
        java.util.Objects.requireNonNull(value, "value");
        requireBound(value.getClass());
        marshalStax(value, XmlBindingMetadata.rootName(value.getClass()), false, output);
    }

    public void marshal(Object value, javax.xml.stream.XMLEventWriter output) {
        marshal(value, EventXmlStreamWriters.events(output));
    }

    public void marshal(Object value, org.xml.sax.ContentHandler output) {
        marshal(value, EventXmlStreamWriters.sax(output));
    }

    public void marshalElement(Object value, String namespace, String localName, boolean nil,
            javax.xml.stream.XMLEventWriter output) {
        marshalElement(value, namespace, localName, nil, EventXmlStreamWriters.events(output));
    }

    public void marshalElement(Object value, String namespace, String localName, boolean nil,
            org.xml.sax.ContentHandler output) {
        marshalElement(value, namespace, localName, nil, EventXmlStreamWriters.sax(output));
    }

    /** Direct StAX counterpart of the explicit-root/JAXBElement path. */
    public void marshalElement(Object value, String namespace, String localName, boolean nil,
            javax.xml.stream.XMLStreamWriter output) {
        java.util.Objects.requireNonNull(localName, "localName");
        if (value != null && !XmlBindingMetadata.scalar(value.getClass())) requireBound(value.getClass());
        marshalStax(value, new XmlExpandedName(namespace, localName), nil || value == null, output);
    }

    private void marshalStax(Object value, XmlExpandedName name, boolean nil,
            javax.xml.stream.XMLStreamWriter output) {
        try {
            if (xmlDeclaration) output.writeStartDocument("UTF-8", "1.0");
            if (nil) {
                writeStaxStart(output, name, XmlExpandedName.EMPTY_NAMESPACE);
                output.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
                output.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "nil", "true");
                output.writeEndElement();
            } else writeStaxObject(value, name, XmlExpandedName.EMPTY_NAMESPACE, output);
            if (xmlDeclaration) output.writeEndDocument();
            output.flush();
        } catch (javax.xml.stream.XMLStreamException failure) {
            throw new XmlBindingException("Cannot write StAX XML", failure);
        }
    }

    private void writeStaxObject(Object value, XmlExpandedName elementName, String inheritedDefault,
            javax.xml.stream.XMLStreamWriter output) throws javax.xml.stream.XMLStreamException {
        writeStaxObject(value,elementName,inheritedDefault,output,null);
    }
    private void writeStaxObject(Object value, XmlExpandedName elementName, String inheritedDefault,
            javax.xml.stream.XMLStreamWriter output, Class<?> declaredType) throws javax.xml.stream.XMLStreamException {
        Class<?> type = value.getClass();
        String activeDefault = writeStaxStart(output, elementName, inheritedDefault);
        writeStaxTypeAttribute(type,declaredType,output);
        if (XmlBindingMetadata.scalar(type)) {
            if(value instanceof javax.xml.namespace.QName){javax.xml.namespace.QName q=(javax.xml.namespace.QName)value;String prefix=q.getPrefix().isEmpty()?"q":q.getPrefix();if(!q.getNamespaceURI().isEmpty())output.writeNamespace(prefix,q.getNamespaceURI());output.writeCharacters(q.getNamespaceURI().isEmpty()?q.getLocalPart():prefix+":"+q.getLocalPart());}
            else output.writeCharacters(XmlBindingMetadata.lexical(value)); output.writeEndElement(); return;
        }
        java.util.Map<String, String> prefixes = new java.util.LinkedHashMap<String, String>();
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.attributes(type)) {
            String namespace = property.expandedName().namespace();
            if (!namespace.isEmpty() && !prefixes.containsKey(namespace)) {
                String prefix = "ns" + (prefixes.size() + 1);
                prefixes.put(namespace, prefix); output.writeNamespace(prefix, namespace);
            }
        }
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.attributes(type)) {
            Object fieldValue = property.read(value);
            if (fieldValue == null) continue;
            String lexical = property.lexical(marshalAdapted(property, fieldValue));
            String namespace = property.expandedName().namespace();
            if (namespace.isEmpty()) output.writeAttribute(property.xmlName(), lexical);
            else output.writeAttribute(prefixes.get(namespace), namespace, property.xmlName(), lexical);
        }
        XmlBindingMetadata.Property text = XmlBindingMetadata.valueProperty(type);
        if (text != null) {
            Object fieldValue = text.read(value);
            if (fieldValue != null) output.writeCharacters(text.lexical(marshalAdapted(text, fieldValue)));
        }
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.properties(type)) {
            if (property.attribute() || property.value()) continue;
            Object fieldValue = property.read(value);
            if (fieldValue == null) { if(property.nillable()&&!property.list()&&property.wrapperName()==null)writeStaxNil(property.expandedName(),activeDefault,output);continue; }
            if(property.wrapperName()!=null){String wrapperDefault=writeStaxStart(output,property.wrapperName(),activeDefault);writeStaxItems(property,fieldValue,wrapperDefault,output);output.writeEndElement();continue;}
            if (property.list()) {
                if (fieldValue.getClass().isArray()) {
                    int length = java.lang.reflect.Array.getLength(fieldValue);
                    for (int i = 0; i < length; i++) {
                        Object item = java.lang.reflect.Array.get(fieldValue, i);
                        if (item != null) writeStaxProperty(property, item, activeDefault, output);else if(property.nillable())writeStaxNil(property.expandedName(),activeDefault,output);
                    }
                } else for (Object item : (Iterable<?>) fieldValue)
                    if (item != null) writeStaxProperty(property, item, activeDefault, output);else if(property.nillable())writeStaxNil(property.expandedName(),activeDefault,output);
            } else writeStaxProperty(property, fieldValue, activeDefault, output);
        }
        output.writeEndElement();
    }
    private void writeStaxItems(XmlBindingMetadata.Property property,Object fieldValue,String inherited,javax.xml.stream.XMLStreamWriter output)throws javax.xml.stream.XMLStreamException{
        if(fieldValue.getClass().isArray()){int length=java.lang.reflect.Array.getLength(fieldValue);for(int i=0;i<length;i++){Object item=java.lang.reflect.Array.get(fieldValue,i);if(item!=null)writeStaxProperty(property,item,inherited,output);else if(property.nillable())writeStaxNil(property.expandedName(),inherited,output);}}
        else for(Object item:(Iterable<?>)fieldValue)if(item!=null)writeStaxProperty(property,item,inherited,output);else if(property.nillable())writeStaxNil(property.expandedName(),inherited,output);
    }
    private void writeStaxProperty(XmlBindingMetadata.Property property,Object value,String inherited,
            javax.xml.stream.XMLStreamWriter output)throws javax.xml.stream.XMLStreamException{
        Object adapted=marshalAdapted(property,value);
        if (writeStaxAttachment(property, adapted, inherited, output)) return;
        adapted = inlineBinary(property, adapted);
        if(property.hexBinary()){writeStaxStart(output,property.expandedName(),inherited);output.writeCharacters(property.lexical(adapted));output.writeEndElement();}
        else writeStaxObject(adapted,property.expandedName(),inherited,output,property.xmlType());
    }
    private boolean writeStaxAttachment(XmlBindingMetadata.Property property,Object value,String inherited,javax.xml.stream.XMLStreamWriter output)throws javax.xml.stream.XMLStreamException {
        if (attachments == null || value == null || !property.binary() || property.inlineBinary()) return false;
        if (property.attachmentRef()) {
            String uri=attachments.addSwaRefAttachment(value); if (uri == null) return false;
            writeStaxStart(output,property.expandedName(),inherited); output.writeCharacters(uri); output.writeEndElement(); return true;
        }
        if (!attachments.isXopPackage()) return false;
        String cid=attachmentContentId(property,value); if (cid == null) return false;
        writeStaxStart(output,property.expandedName(),inherited);
        output.writeStartElement("xop","Include",XOP_NAMESPACE); output.writeNamespace("xop",XOP_NAMESPACE);
        // href is unqualified on xop:Include, per the XOP recommendation.
        output.writeAttribute("href",href(cid));
        output.writeEndElement(); output.writeEndElement(); return true;
    }

    private static String writeStaxStart(javax.xml.stream.XMLStreamWriter output, XmlExpandedName name,
            String inheritedDefault) throws javax.xml.stream.XMLStreamException {
        String namespace = name.namespace();
        if (namespace.isEmpty()) output.writeStartElement(name.localName());
        else output.writeStartElement("", name.localName(), namespace);
        if (!namespace.equals(inheritedDefault)) output.writeDefaultNamespace(namespace);
        return namespace;
    }

    private static void writeStaxNil(XmlExpandedName name,String inherited,javax.xml.stream.XMLStreamWriter output)throws javax.xml.stream.XMLStreamException{
        writeStaxStart(output,name,inherited);output.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");
        output.writeAttribute("xsi","http://www.w3.org/2001/XMLSchema-instance","nil","true");output.writeEndElement();
    }
    private static void writeStaxTypeAttribute(Class<?> actual,Class<?> declared,javax.xml.stream.XMLStreamWriter output)throws javax.xml.stream.XMLStreamException{
        if(declared==null||declared==actual||!declared.isAssignableFrom(actual))return;
        XmlExpandedName typeName=XmlBindingMetadata.typeName(actual);String prefix="t";output.writeNamespace("xsi","http://www.w3.org/2001/XMLSchema-instance");
        if(typeName.namespace().isEmpty())output.writeAttribute("xsi","http://www.w3.org/2001/XMLSchema-instance","type",typeName.localName());
        else {output.writeNamespace(prefix,typeName.namespace());output.writeAttribute("xsi","http://www.w3.org/2001/XMLSchema-instance","type",prefix+":"+typeName.localName());}
    }

    /** Writes a value under an explicitly supplied root name (for example a JAXB element wrapper). */
    public void marshalElement(Object value, String namespace, String localName, boolean nil, OutputStream output) {
        java.util.Objects.requireNonNull(localName, "localName");
        java.util.Objects.requireNonNull(output, "output");
        if (localName.isEmpty()) throw new XmlBindingException("Element local name must not be empty");
        if (value != null && !XmlBindingMetadata.scalar(value.getClass())) requireBound(value.getClass());
        try {
            writer.reset(output);
            if (xmlDeclaration) writer.raw("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            XmlExpandedName name = new XmlExpandedName(namespace, localName);
            if (nil || value == null) writeNilElement(name);
            else writeObject(value, name, XmlExpandedName.EMPTY_NAMESPACE);
            writer.finish();
        } catch (IOException e) { throw new XmlBindingException("Cannot write XML", e); }
    }

    private void writeNilElement(XmlExpandedName name) throws IOException {
        writer.ascii('<'); writer.raw(name.localName());
        declareDefaultNamespace(name.namespace(), XmlExpandedName.EMPTY_NAMESPACE);
        writer.raw(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"></");
        writer.raw(name.localName()); writer.ascii('>');
    }

    private void writeObject(Object value, XmlExpandedName elementName, String inheritedDefaultNamespace)
            throws IOException { writeObject(value,elementName,inheritedDefaultNamespace,null); }
    private void writeObject(Object value, XmlExpandedName elementName, String inheritedDefaultNamespace,Class<?> declaredType)
            throws IOException {
        Class<?> type = value.getClass();
        writer.ascii('<'); writer.raw(elementName.localName());
        String activeDefault = declareDefaultNamespace(elementName.namespace(), inheritedDefaultNamespace);
        writeTypeAttribute(type,declaredType);
        if (XmlBindingMetadata.scalar(type)) {
            if(value instanceof javax.xml.namespace.QName){javax.xml.namespace.QName q=(javax.xml.namespace.QName)value;String prefix=q.getPrefix().isEmpty()?"q":q.getPrefix();if(!q.getNamespaceURI().isEmpty()){writer.raw(" xmlns:");writer.raw(prefix);writer.raw("=\"");writer.attribute(q.getNamespaceURI());writer.ascii('"');}writer.ascii('>');writer.text(q.getNamespaceURI().isEmpty()?q.getLocalPart():prefix+":"+q.getLocalPart());}
            else { writer.ascii('>'); writer.text(XmlBindingMetadata.lexical(value)); }
            close(elementName.localName()); return;
        }
        java.util.Map<String, String> attributePrefixes = new java.util.LinkedHashMap<String, String>();
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.attributes(type)) {
            String namespace = property.expandedName().namespace();
            if (!namespace.isEmpty() && !attributePrefixes.containsKey(namespace)) {
                String prefix = "ns" + (attributePrefixes.size() + 1);
                attributePrefixes.put(namespace, prefix);
                writer.raw(" xmlns:"); writer.raw(prefix); writer.raw("=\"");
                writer.attribute(namespace); writer.ascii('"');
            }
        }
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.attributes(type)) {
            Object fieldValue = property.read(value);
            if (fieldValue == null) continue;
            fieldValue = marshalAdapted(property, fieldValue);
            writer.ascii(' ');
            String prefix = attributePrefixes.get(property.expandedName().namespace());
            if (prefix != null) { writer.raw(prefix); writer.ascii(':'); }
            writer.raw(property.xmlName()); writer.raw("=\"");
            writer.attribute(property.lexical(fieldValue)); writer.ascii('"');
        }
        writer.ascii('>');
        XmlBindingMetadata.Property text = XmlBindingMetadata.valueProperty(type);
        if (text != null) {
            Object fieldValue = text.read(value);
            if (fieldValue != null) writer.text(text.lexical(marshalAdapted(text, fieldValue)));
        }
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.properties(type)) {
            if (property.attribute() || property.value()) continue;
            Object fieldValue = property.read(value);
            if (fieldValue == null) { if(property.nillable()&&!property.list()&&property.wrapperName()==null)writeNilElement(property.expandedName());continue; }
            if(property.wrapperName()!=null){writer.ascii('<');writer.raw(property.wrapperName().localName());String wrapperDefault=declareDefaultNamespace(property.wrapperName().namespace(),activeDefault);writer.ascii('>');writeItems(property,fieldValue,wrapperDefault);close(property.wrapperName().localName());continue;}
            if (property.list()) {
                if (fieldValue.getClass().isArray()) {
                    int length = java.lang.reflect.Array.getLength(fieldValue);
                    for (int i = 0; i < length; i++) {
                        Object item = java.lang.reflect.Array.get(fieldValue, i);
                        if (item != null) writeProperty(property,item,activeDefault);else if(property.nillable())writeNilElement(property.expandedName());
                    }
                } else for (Object item : (Iterable<?>) fieldValue)
                    if (item != null) writeProperty(property,item,activeDefault);else if(property.nillable())writeNilElement(property.expandedName());
            } else writeProperty(property,fieldValue,activeDefault);
        }
        close(elementName.localName());
    }
    private void writeItems(XmlBindingMetadata.Property property,Object fieldValue,String inherited)throws IOException{
        if(fieldValue.getClass().isArray()){int length=java.lang.reflect.Array.getLength(fieldValue);for(int i=0;i<length;i++){Object item=java.lang.reflect.Array.get(fieldValue,i);if(item!=null)writeProperty(property,item,inherited);else if(property.nillable())writeNilElement(property.expandedName());}}
        else for(Object item:(Iterable<?>)fieldValue)if(item!=null)writeProperty(property,item,inherited);else if(property.nillable())writeNilElement(property.expandedName());
    }
    private void writeProperty(XmlBindingMetadata.Property property,Object value,String inherited)throws IOException{
        Object adapted=marshalAdapted(property,value);
        if (writeAttachment(property, adapted, inherited)) return;
        adapted = inlineBinary(property, adapted);
        if(property.hexBinary()){writer.ascii('<');writer.raw(property.xmlName());String active=declareDefaultNamespace(property.expandedName().namespace(),inherited);writer.ascii('>');writer.text(property.lexical(adapted));close(property.xmlName());}
        else writeObject(adapted,property.expandedName(),inherited,property.xmlType());
    }
    private boolean writeAttachment(XmlBindingMetadata.Property property,Object value,String inherited)throws IOException {
        // Gated on flags precomputed per class: no annotation lookup happens in the marshal loop.
        if (attachments == null || value == null || !property.binary() || property.inlineBinary()) return false;
        if (property.attachmentRef()) {
            String uri=attachments.addSwaRefAttachment(value); if (uri == null) return false;
            writer.ascii('<'); writer.raw(property.xmlName()); declareDefaultNamespace(property.expandedName().namespace(),inherited);
            writer.ascii('>'); writer.text(uri); close(property.xmlName()); return true;
        }
        if (!attachments.isXopPackage()) return false;
        String cid=attachmentContentId(property,value); if (cid == null) return false;
        writer.ascii('<'); writer.raw(property.xmlName()); declareDefaultNamespace(property.expandedName().namespace(),inherited); writer.raw(" xmlns:xop=\""+XOP_NAMESPACE+"\"><xop:Include href=\""); writer.attribute(href(cid)); writer.raw("\"></xop:Include>"); close(property.xmlName()); return true;
    }

    /** Shared by the byte and StAX writers: one attachment registration, no intermediate copy. */
    private String attachmentContentId(XmlBindingMetadata.Property property,Object value) {
        String namespace=property.expandedName().namespace(), local=property.xmlName();
        if (value instanceof byte[]) {
            byte[] data=(byte[]) value;
            return attachments.addMtomAttachment(data,0,data.length,mimeType(property,value),namespace,local);
        }
        String cid=attachments.addMtomAttachment(value,namespace,local);
        if (cid != null) return cid;
        byte[] data=attachments.toBytes(value); if (data == null) return null;
        return attachments.addMtomAttachment(data,0,data.length,mimeType(property,value),namespace,local);
    }

    /** {@code @XmlMimeType} wins over whatever the value itself reports. */
    private String mimeType(XmlBindingMetadata.Property property,Object value) {
        String declared=property.mimeType();
        return declared != null ? declared : attachments.contentType(value);
    }

    /**
     * Base64 fallback for a binary property that did not become an attachment. {@code byte[]} is
     * already handled by the lexical mapping; a {@code DataHandler} has to be read through the
     * bridge, since the core cannot see the Activation API.
     */
    private Object inlineBinary(XmlBindingMetadata.Property property, Object value) {
        if (!property.binary() || value == null || value instanceof byte[] || attachments == null) return value;
        byte[] data = attachments.toBytes(value);
        return data == null ? value : data;
    }

    private static final String XOP_NAMESPACE = "http://www.w3.org/2004/08/xop/include";

    private static String href(String contentId) {
        return contentId.startsWith("cid:") ? contentId : "cid:" + contentId;
    }
    private void writeTypeAttribute(Class<?> actual,Class<?> declared)throws IOException{
        if(declared==null||declared==actual||!declared.isAssignableFrom(actual))return;
        XmlExpandedName typeName=XmlBindingMetadata.typeName(actual);writer.raw(" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");writer.raw(" xsi:type=\"");
        if(typeName.namespace().isEmpty())writer.raw(typeName.localName());else{writer.raw("t:");writer.raw(typeName.localName());writer.raw("\" xmlns:t=\"");writer.attribute(typeName.namespace());}
        writer.ascii('"');
    }
    private Object marshalAdapted(XmlBindingMetadata.Property property, Object value) {
        if (property.adapterType() == null) return value;
        XmlBindingAdapter adapter = adapters.get(property.adapterType());
        if (adapter == null) throw new XmlBindingException("No XmlAdapter registered for " + property.adapterType().getName());
        try { return adapter.marshal(value); }
        catch (Exception failure) { throw new XmlBindingException("XmlAdapter marshal failed", failure); }
    }

    private String declareDefaultNamespace(String namespace, String inherited) throws IOException {
        if (namespace.equals(inherited)) return inherited;
        writer.raw(" xmlns=\""); writer.attribute(namespace); writer.ascii('"');
        return namespace;
    }

    private void close(String name) throws IOException {
        writer.raw("</"); writer.raw(name); writer.ascii('>');
    }
    private void requireBound(Class<?> type) {
        if (!boundTypes.contains(type)) throw new XmlBindingException("Type is not bound to this context: " + type.getName());
    }
}
