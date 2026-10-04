package org.simdxml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Executes a precompiled JAXB-style binding plan against the streaming reader. */
final class XmlBinder {
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final XmlExpandedName XSI_NIL = new XmlExpandedName(XSI_NAMESPACE, "nil");
    private static final XmlExpandedName XSI_TYPE = new XmlExpandedName(XSI_NAMESPACE, "type");
    /** Scalar text peeked ahead of the reader's own flyweight; request-local, like the binder. */
    private final XmlByteSlice pendingText = new XmlByteSlice();
    private final Map<Class<?>, XmlBindingAdapter> adapters;
    private XmlAttachmentHandler attachments;
    XmlBinder() { this(java.util.Collections.<Class<?>, XmlBindingAdapter>emptyMap()); }
    XmlBinder(Map<Class<?>, XmlBindingAdapter> adapters) { this.adapters = adapters; }
    void withAttachmentHandler(XmlAttachmentHandler handler) { attachments = handler; }
    <T> T bind(BindingXmlReader reader, Class<T> type) {
        advanceToRoot(reader);
        NamespaceFrame scope = NamespaceFrame.enter(reader, null);
        XmlExpandedName expected = XmlBindingMetadata.rootName(type);
        if (!matchesElement(reader.name(), expected, scope))
            throw new XmlBindingException("Expected root <" + expected + "> but found <" + reader.name() + ">");
        return type.cast(readObject(reader, type, scope));
    }

    Object bindAny(BindingXmlReader reader, Map<XmlExpandedName, Class<?>> roots) {
        advanceToRoot(reader);
        NamespaceFrame scope = NamespaceFrame.enter(reader, null);
        XmlExpandedName name = elementName(reader.name(), scope);
        Class<?> type = roots.get(name);
        if (type == null) throw new XmlBindingException("No bound class for root element <" + name + ">");
        return readObject(reader, type, scope);
    }

    private static void advanceToRoot(BindingXmlReader reader) {
        XmlEvent event;
        do event = reader.next(); while (event != XmlEvent.START_ELEMENT && event != XmlEvent.END_DOCUMENT);
        if (event == XmlEvent.END_DOCUMENT) throw new XmlBindingException("Document has no root element");
    }

    private Object readObject(BindingXmlReader reader, Class<?> type, NamespaceFrame scope) {
        type = resolveXsiType(reader, type, scope);
        if (XmlBindingMetadata.scalar(type)) {
            String text=readElementText(reader);
            return type==javax.xml.namespace.QName.class?scope.resolveQName(text):XmlBindingMetadata.convert(text,type);
        }
        BindingPlan plan = XmlBindingMetadata.plan(type);
        List<XmlBindingMetadata.Property> properties = plan.properties;
        // Everything but the scope is precomputed on the plan; see BindingPlan.directBeanCapable().
        if (scope == NamespaceFrame.EMPTY && plan.directBeanCapable())
            return readDirectBean(reader, type, scope, plan);
        Object[] values = new Object[properties.size()];
        Map<XmlExpandedName, XmlBindingMetadata.Property> children = plan.children;
        Map<String, XmlBindingMetadata.Property> localChildren = plan.localChildren;
        Map<XmlExpandedName, XmlBindingMetadata.Property> wrappers = plan.wrappers;
        XmlBindingMetadata.Property valueProperty = plan.value;
        for (XmlBindingMetadata.Property property : plan.attributes) {
            String raw = scope == NamespaceFrame.EMPTY && property.expandedName().namespace().isEmpty()
                    ? reader.attribute(property.xmlName()) : attribute(reader, property.expandedName(), scope);
            if (raw != null) values[property.index()] = unmarshalAdapted(property,
                    property.xmlType()==javax.xml.namespace.QName.class?scope.resolveQName(raw):property.convert(raw));
        }
        String text = null;
        StringBuilder textBuilder = null;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) {
                String part = reader.text();
                if (textBuilder != null) textBuilder.append(part);
                else if (text == null) text = part;
                else {
                    if (textBuilder == null) textBuilder = new StringBuilder(text.length() + part.length());
                    textBuilder.append(text).append(part);
                    text = null;
                }
            }
            else if (event == XmlEvent.START_ELEMENT) {
                NamespaceFrame childScope = NamespaceFrame.enter(reader, scope);
                XmlBindingMetadata.Property property = null;
                String qualifiedName = null;
                if (childScope == NamespaceFrame.EMPTY && reader.hasByteNames() && plan.hashDispatch()) {
                    property = plan.findLocal(reader);
                } else {
                    qualifiedName = reader.name();
                    property = childScope == NamespaceFrame.EMPTY && qualifiedName.indexOf(':') < 0
                            ? localChildren.get(qualifiedName) : children.get(elementName(qualifiedName, childScope));
                }
                if (property == null) {
                    if (qualifiedName == null) qualifiedName = reader.name();
                    XmlBindingMetadata.Property wrapped=wrappers.get(elementName(qualifiedName,childScope));
                    if(wrapped==null)skipElement(reader);else values[wrapped.index()]=readWrapped(reader,wrapped,childScope);
                }
                else if (property.list()) {
                    @SuppressWarnings("unchecked") List<Object> list = (List<Object>) values[property.index()];
                    if (list == null) values[property.index()] = list = new ArrayList<>();
                    Object parsed=readProperty(reader,property,childScope);
                    if(parsed!=null||property.nillable())list.add(parsed==null?null:unmarshalAdapted(property, parsed));
                } else {
                    Object parsed=readProperty(reader,property,childScope);
                    values[property.index()] = parsed==null?null:unmarshalAdapted(property,parsed);
                }
            } else if (event == XmlEvent.END_ELEMENT) break;
        }
        if (valueProperty != null) {
            String lexical = textBuilder == null ? (text == null ? "" : text) : textBuilder.append(text == null ? "" : text).toString();
            values[valueProperty.index()] = unmarshalAdapted(valueProperty,
                    valueProperty.xmlType()==javax.xml.namespace.QName.class?scope.resolveQName(lexical):valueProperty.convert(lexical));
        }
        return construct(type, properties, values);
    }

    /**
     * Allocation-minimal path for the common schema: no namespaces, adapters or wrappers.
     * The bean is created before reading children and properties are written immediately, so
     * there is no per-object Object[] staging array or final reflection pass.
     */
    private Object readDirectBean(BindingXmlReader reader, Class<?> type, NamespaceFrame scope, BindingPlan plan) {
        final GeneratedBeanAccess generated = plan.generated;
        final Object instance;
        try { instance = generated == null ? XmlBindingMetadata.constructor(type).newInstance() : generated.newInstance(); }
        catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot construct " + type.getName(), e); }
        for (XmlBindingMetadata.Property property : plan.attributes) {
            byte[] name = property.xmlNameBytes();
            boolean byteNames = reader.hasByteNames() && name != null;
            XmlByteSlice rawBytes = byteNames
                    ? reader.rawAttributeBytes(name) : reader.rawAttributeBytes(property.xmlName());
            if (rawBytes != null && rawConvertible(property.xmlType()))
                writeDirect(generated, property, instance, property.convert(rawBytes));
            else {
                String raw = byteNames ? reader.attribute(name) : reader.attribute(property.xmlName());
                if (raw != null) writeDirect(generated, property, instance, property.convert(raw));
            }
        }
        String text = null;
        StringBuilder textBuilder = null;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) {
                String part = reader.text();
                if (textBuilder != null) textBuilder.append(part);
                else if (text == null) text = part;
                else { textBuilder = new StringBuilder(text.length() + part.length()); textBuilder.append(text).append(part); text = null; }
            } else if (event == XmlEvent.START_ELEMENT) {
                NamespaceFrame childScope = NamespaceFrame.enter(reader, scope);
                XmlBindingMetadata.Property property = childScope == NamespaceFrame.EMPTY
                        ? (reader.hasByteNames() && plan.hashDispatch()
                                ? plan.findLocal(reader) : plan.localChildren.get(reader.name()))
                        : plan.children.get(elementName(reader.name(), childScope));
                if (property == null) { skipElement(reader); continue; }
                Object parsed = readProperty(reader, property, childScope);
                if (property.list()) {
                    @SuppressWarnings("unchecked") List<Object> list = (List<Object>) (generated == null ? property.read(instance) : generated.read(instance, property.index()));
                    if (list == null) { list = new ArrayList<Object>(); writeDirect(generated, property, instance, list); }
                    if (parsed != null || property.nillable()) list.add(parsed == null ? null : unmarshalAdapted(property, parsed));
                } else if (parsed != null || property.nillable()) {
                    writeDirect(generated, property, instance, unmarshalAdapted(property, parsed));
                }
            } else if (event == XmlEvent.END_ELEMENT) break;
        }
        if (plan.value != null) {
            String lexical = textBuilder == null ? (text == null ? "" : text)
                    : textBuilder.append(text == null ? "" : text).toString();
            Object value = plan.value.xmlType() == javax.xml.namespace.QName.class
                    ? scope.resolveQName(lexical) : plan.value.convert(lexical);
            writeDirect(generated, plan.value, instance, unmarshalAdapted(plan.value, value));
        }
        return instance;
    }

    private static void writeDirect(XmlBindingMetadata.Property property, Object target, Object value) {
        try { property.write(target, value); }
        catch (IllegalAccessException e) { throw new XmlBindingException("Cannot write property " + property.xmlName(), e); }
    }
    private static void writeDirect(GeneratedBeanAccess generated, XmlBindingMetadata.Property property, Object target, Object value) {
        if (generated != null) { generated.write(target, property.index(), value); return; }
        writeDirect(property, target, value);
    }
    /** Types {@code Property.convert(XmlByteSlice)} converts from bytes without building a String. */
    private static boolean rawConvertible(Class<?> type) {
        return type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class || type == byte.class || type == Byte.class
                || type == boolean.class || type == Boolean.class
                || type == double.class || type == Double.class || type == float.class || type == Float.class;
    }

    private static Class<?> resolveXsiType(BindingXmlReader reader, Class<?> declared, NamespaceFrame scope) {
        if (scope == NamespaceFrame.EMPTY) return declared;
        String lexical = attribute(reader, XSI_TYPE, scope);
        if(lexical==null||lexical.isEmpty())return declared;
        javax.xml.namespace.QName qname=scope.resolveQName(lexical);
        Class<?> resolved=XmlBindingMetadata.polymorphicTypes(declared).get(new XmlExpandedName(qname.getNamespaceURI(),qname.getLocalPart()));
        if(resolved==null||!declared.isAssignableFrom(resolved))throw new XmlBindingException("Unknown xsi:type "+qname+" for "+declared.getName());
        return resolved;
    }
    private Object readWrapped(BindingXmlReader reader,XmlBindingMetadata.Property property,NamespaceFrame wrapperScope){
        List<Object> list=new ArrayList<Object>();
        while(reader.hasNext()){
            XmlEvent event=reader.next();
            if(event==XmlEvent.START_ELEMENT){NamespaceFrame itemScope=NamespaceFrame.enter(reader,wrapperScope);if(matchesElement(reader.name(),property.expandedName(),itemScope)){
                Object parsed=readProperty(reader,property,itemScope);
                if(parsed!=null||property.nillable())list.add(parsed==null?null:unmarshalAdapted(property,parsed));
            }else skipElement(reader);
            }else if(event==XmlEvent.END_ELEMENT)break;
        }
        return list;
    }

    /** Sentinel: the element was not an attachment reference, so the caller resumes the scalar path. */
    private static final Object NOT_AN_ATTACHMENT = new Object();

    /**
     * Binds {@code @XmlAttachmentRef} (swaRef) and {@code <xop:Include/>}. Returns
     * {@link #NOT_AN_ATTACHMENT} when the content turns out to be inline base64, having consumed
     * at most one event, which is handed back to the scalar path as {@code pending}.
     */
    private Object readAttachment(BindingXmlReader reader, XmlBindingMetadata.Property property, NamespaceFrame scope) {
        if (property.attachmentRef()) {
            String uri = readElementText(reader);
            if (uri.isEmpty()) return null;
            return attachmentValue(property, uri.startsWith("cid:") ? uri.substring(4) : uri);
        }
        XmlEvent next = null;
        boolean peeked = false;
        if (!property.inlineBinary() && attachments.isXopPackage()) {
            next = reader.hasNext() ? reader.next() : null;
            peeked = true;
            if (next == XmlEvent.START_ELEMENT) {
                // xmlns:xop is commonly declared on the Include element itself, not on the parent.
                XmlExpandedName name = elementName(reader.name(), NamespaceFrame.enter(reader, scope));
                if (XOP_INCLUDE_NAMESPACE.equals(name.namespace()) && "Include".equals(name.localName())) {
                    String href = reader.attribute("href");
                    if (href == null || !href.startsWith("cid:")) throw new XmlBindingException("Invalid XOP href");
                    skipElement(reader);
                    // skipElement stops at </xop:Include>; the enclosing property element is still open.
                    consumeToEndElement(reader);
                    return attachmentValue(property, href.substring(4));
                }
            }
        }
        // Inline base64. byte[] with nothing consumed goes back to the scalar fast path untouched.
        if (property.xmlType() == byte[].class)
            return peeked ? finishScalar(property, scope, readElementText(reader, null, next)) : NOT_AN_ATTACHMENT;
        String text = readElementText(reader, null, next);
        if (text.isEmpty()) return null;
        return attachments.fromBytes(java.util.Base64.getMimeDecoder().decode(text.trim()), property.mimeType());
    }

    private Object attachmentValue(XmlBindingMetadata.Property property, String contentId) {
        return attachmentValue(attachments, property, contentId);
    }

    /** Prefers the provider's own DataHandler so that no byte[] is materialized when it is not needed. */
    static Object attachmentValue(XmlAttachmentHandler attachments, XmlBindingMetadata.Property property, String contentId) {
        if (property.xmlType() == byte[].class) return attachments.getAttachmentAsByteArray(contentId);
        Object handler = attachments.getAttachmentAsDataHandler(contentId);
        if (handler != null) return handler;
        byte[] data = attachments.getAttachmentAsByteArray(contentId);
        return data == null ? null : attachments.fromBytes(data, property.mimeType());
    }

    static final String XOP_INCLUDE_NAMESPACE = "http://www.w3.org/2004/08/xop/include";

    /** Consumes the remainder of the current element, tolerating whitespace after a child. */
    private static void consumeToEndElement(BindingXmlReader reader) {
        int depth = 0;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) depth++;
            else if (event == XmlEvent.END_ELEMENT) { if (depth == 0) return; depth--; }
        }
        throw new XmlBindingException("Unexpected end of document");
    }

    private static Object finishScalar(XmlBindingMetadata.Property property, NamespaceFrame scope, String text) {
        if (text.isEmpty() && property.defaultValue() != null) text = property.defaultValue();
        if (property.xmlType() == javax.xml.namespace.QName.class) return scope.resolveQName(text);
        return property.convert(text);
    }

    private Object readProperty(BindingXmlReader reader,XmlBindingMetadata.Property property,NamespaceFrame scope){
        if(isNil(reader,scope)){skipElement(reader);return null;}
        boolean binary = property.binary() && attachments != null;
        if(property.hexBinary()||binary||XmlBindingMetadata.scalar(property.xmlType())){
            if (binary) {
                Object attachment = readAttachment(reader, property, scope);
                if (attachment != NOT_AN_ATTACHMENT) return attachment;
            }
            if (rawConvertible(property.xmlType()) && property.defaultValue() == null && reader.hasByteNames()) {
                XmlEvent first = reader.next();
                if (first == XmlEvent.TEXT || first == XmlEvent.CDATA) {
                    XmlByteSlice raw = reader.rawTextBytes();
                    if (raw != null && !raw.contains((byte) '&')) {
                        // The flyweight is reader-owned and moves on the next event: keep our own view.
                        pendingText.reset(raw);
                        XmlEvent second = reader.hasNext() ? reader.next() : null;
                        if (second == XmlEvent.END_ELEMENT) {
                            pendingText.resetAsciiTrimmed(pendingText);
                            return property.convert(pendingText);
                        }
                        // Mixed content after the first segment: resume the general path without losing it.
                        return finishScalar(property, scope, readElementText(reader, pendingText.decodeUtf8(), second));
                    }
                }
                return finishScalar(property, scope, readElementText(reader, null, first));
            }
            return finishScalar(property, scope, readElementText(reader));
        }
        return readObject(reader,property.xmlType(),scope);
    }

    private static boolean isNil(BindingXmlReader reader,NamespaceFrame scope){
        // No prefix can be bound to the XSI namespace in an empty scope, so no attribute can match.
        if (scope == NamespaceFrame.EMPTY) return false;
        String value = attribute(reader, XSI_NIL, scope);
        return "true".equals(value)||"1".equals(value);
    }

    private Object unmarshalAdapted(XmlBindingMetadata.Property property, Object value) {
        if (property.adapterType() == null) return value;
        XmlBindingAdapter adapter = adapters.get(property.adapterType());
        if (adapter == null) throw new XmlBindingException("No XmlAdapter registered for " + property.adapterType().getName());
        try { return adapter.unmarshal(value); }
        catch (Exception failure) { throw new XmlBindingException("XmlAdapter unmarshal failed", failure); }
    }

    private static String readElementText(BindingXmlReader reader) { return readElementText(reader, null, null); }

    /**
     * Accumulates the text of a scalar element. {@code prefix} is text already consumed by a caller
     * that peeked ahead, and {@code pending} an event it already read; both may be null.
     */
    private static String readElementText(BindingXmlReader reader, String prefix, XmlEvent pending) {
        String text = prefix;
        StringBuilder builder = null;
        XmlEvent event = pending;
        while (true) {
            if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) {
                String part = reader.text();
                if (builder != null) builder.append(part);
                else if (text == null) text = part;
                else {
                    builder = new StringBuilder(text.length() + part.length());
                    builder.append(text).append(part);
                    text = null;
                }
            }
            else if (event == XmlEvent.START_ELEMENT)
                throw new XmlBindingException("Scalar element contains child <" + reader.name() + ">");
            else if (event == XmlEvent.END_ELEMENT)
                return builder == null ? (text == null ? "" : text) : builder.append(text == null ? "" : text).toString();
            if (!reader.hasNext()) break;
            event = reader.next();
        }
        throw new XmlBindingException("Unexpected end of document");
    }

    private static void skipElement(BindingXmlReader reader) {
        int depth = 1;
        while (depth != 0 && reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) depth++;
            else if (event == XmlEvent.END_ELEMENT) depth--;
        }
    }

    private static String attribute(BindingXmlReader reader, XmlExpandedName expected, NamespaceFrame scope) {
        // The overwhelmingly common JAXB attribute is unqualified. Avoid entrySet(), which
        // materializes temporary Entry/Set objects in SmallAttributeMap on every lookup.
        if (expected.namespace().isEmpty()) return reader.attribute(expected.localName());
        for (Map.Entry<String, String> entry : reader.attributes().entrySet()) {
            String qualified = entry.getKey();
            if (qualified.equals("xmlns") || qualified.startsWith("xmlns:")) continue;
            if (attributeName(qualified, scope).equals(expected)) return entry.getValue();
        }
        return null;
    }

    private static XmlExpandedName elementName(String qualified, NamespaceFrame scope) {
        int colon = qualified.indexOf(':');
        String prefix = colon < 0 ? "" : qualified.substring(0, colon);
        String local = colon < 0 ? qualified : qualified.substring(colon + 1);
        return new XmlExpandedName(scope.resolve(prefix), local);
    }

    private static boolean matchesElement(String qualified, XmlExpandedName expected, NamespaceFrame scope) {
        if (scope == NamespaceFrame.EMPTY && expected.namespace().isEmpty() && qualified.indexOf(':') < 0)
            return expected.localName().equals(qualified);
        return expected.equals(elementName(qualified, scope));
    }

    private static XmlExpandedName attributeName(String qualified, NamespaceFrame scope) {
        int colon = qualified.indexOf(':');
        if (colon < 0) return new XmlExpandedName(XmlExpandedName.EMPTY_NAMESPACE, qualified);
        return new XmlExpandedName(scope.resolve(qualified.substring(0, colon)), qualified.substring(colon + 1));
    }

    /** Immutable linked namespace scope; a frame is allocated only on elements declaring xmlns. */
    private static final class NamespaceFrame {
        private static final String XML_URI = "http://www.w3.org/XML/1998/namespace";
        private static final NamespaceFrame EMPTY = new NamespaceFrame(null, new String[0], new String[0]);
        private final NamespaceFrame parent;
        private final String[] prefixes;
        private final String[] uris;

        private NamespaceFrame(NamespaceFrame parent, String[] prefixes, String[] uris) {
            this.parent = parent; this.prefixes = prefixes; this.uris = uris;
        }

        static NamespaceFrame enter(BindingXmlReader reader, NamespaceFrame parent) {
            if (!reader.hasNamespaceDeclarations()) return parent == null ? EMPTY : parent;
            // SmallAttributeMap materializes a fresh entry set per call: reuse one view.
            java.util.Set<Map.Entry<String, String>> entries = reader.attributes().entrySet();
            int count = 0;
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                if (name.equals("xmlns") || name.startsWith("xmlns:")) count++;
            }
            if (count == 0) return parent == null ? EMPTY : parent;
            String[] prefixes = new String[count], uris = new String[count];
            int index = 0;
            for (Map.Entry<String, String> entry : entries) {
                String name = entry.getKey();
                if (!name.equals("xmlns") && !name.startsWith("xmlns:")) continue;
                prefixes[index] = name.equals("xmlns") ? "" : name.substring(6);
                uris[index++] = entry.getValue();
            }
            return new NamespaceFrame(parent, prefixes, uris);
        }

        String resolve(String prefix) {
            if ("xml".equals(prefix)) return XML_URI;
            for (NamespaceFrame frame = this; frame != null; frame = frame.parent)
                for (int i = frame.prefixes.length - 1; i >= 0; i--)
                    if (frame.prefixes[i].equals(prefix)) return frame.uris[i];
            if (!prefix.isEmpty()) throw new XmlBindingException("Unbound XML namespace prefix: " + prefix);
            return XmlExpandedName.EMPTY_NAMESPACE;
        }
        javax.xml.namespace.QName resolveQName(String lexical) {
            String value=lexical.trim();int colon=value.indexOf(':');String prefix=colon<0?"":value.substring(0,colon);
            String local=colon<0?value:value.substring(colon+1);return new javax.xml.namespace.QName(resolve(prefix),local,prefix);
        }
    }

    static Object construct(Class<?> type, List<XmlBindingMetadata.Property> properties, Object[] values) {
        try {
            Object instance = XmlBindingMetadata.constructor(type).newInstance();
            for (XmlBindingMetadata.Property property : properties)
                if (values[property.index()] != null) property.write(instance,
                        property.requiresMaterialization()
                                ? materializeMultiple(property, values[property.index()]) : values[property.index()]);
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new XmlBindingException("Cannot construct " + type.getName(), e);
        }
    }

    static Object materializeMultiple(XmlBindingMetadata.Property property, Object value)
            throws ReflectiveOperationException {
        @SuppressWarnings("unchecked") List<Object> items = (List<Object>) value;
        Class<?> raw = property.rawType();
        if (raw.isArray()) {
            Object array = java.lang.reflect.Array.newInstance(property.itemType(), items.size());
            for (int i = 0; i < items.size(); i++) java.lang.reflect.Array.set(array, i, items.get(i));
            return array;
        }
        if (raw.isAssignableFrom(ArrayList.class)) return items;
        java.util.Collection<Object> collection;
        if (raw.isInterface()) {
            if (java.util.Set.class.isAssignableFrom(raw)) collection = new java.util.LinkedHashSet<Object>();
            else if (java.util.Queue.class.isAssignableFrom(raw)) collection = new java.util.ArrayDeque<Object>();
            else collection = new ArrayList<Object>();
        } else {
            @SuppressWarnings("unchecked") java.util.Collection<Object> created =
                    (java.util.Collection<Object>) raw.getDeclaredConstructor().newInstance();
            collection = created;
        }
        collection.addAll(items);
        return collection;
    }

}
