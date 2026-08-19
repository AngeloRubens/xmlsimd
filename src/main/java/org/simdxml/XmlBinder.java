package org.simdxml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Executes a precompiled JAXB-style binding plan against the streaming reader. */
final class XmlBinder {
    private final Map<Class<?>, XmlBindingAdapter> adapters;
    XmlBinder() { this(java.util.Collections.<Class<?>, XmlBindingAdapter>emptyMap()); }
    XmlBinder(Map<Class<?>, XmlBindingAdapter> adapters) { this.adapters = adapters; }
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
        List<XmlBindingMetadata.Property> properties = XmlBindingMetadata.properties(type);
        Object[] values = new Object[properties.size()];
        Map<XmlExpandedName, XmlBindingMetadata.Property> children = XmlBindingMetadata.children(type);
        Map<String, XmlBindingMetadata.Property> localChildren = XmlBindingMetadata.localChildren(type);
        Map<XmlExpandedName, XmlBindingMetadata.Property> wrappers = XmlBindingMetadata.wrappers(type);
        XmlBindingMetadata.Property valueProperty = XmlBindingMetadata.valueProperty(type);
        for (XmlBindingMetadata.Property property : XmlBindingMetadata.attributes(type)) {
            String raw = scope == NamespaceFrame.EMPTY && property.expandedName().namespace().isEmpty()
                    ? reader.attribute(property.xmlName()) : attribute(reader, property.expandedName(), scope);
            if (raw != null) values[property.index()] = unmarshalAdapted(property,
                    property.xmlType()==javax.xml.namespace.QName.class?scope.resolveQName(raw):property.convert(raw));
        }
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) text.append(reader.text());
            else if (event == XmlEvent.START_ELEMENT) {
                NamespaceFrame childScope = NamespaceFrame.enter(reader, scope);
                String qualifiedName = reader.name();
                XmlBindingMetadata.Property property = childScope == NamespaceFrame.EMPTY
                        && qualifiedName.indexOf(':') < 0
                        ? localChildren.get(qualifiedName)
                        : children.get(elementName(qualifiedName, childScope));
                if (property == null) {
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
        if (valueProperty != null)
            values[valueProperty.index()] = unmarshalAdapted(valueProperty,
                    valueProperty.xmlType()==javax.xml.namespace.QName.class?scope.resolveQName(text.toString()):valueProperty.convert(text.toString()));
        return construct(type, properties, values);
    }

    private static Class<?> resolveXsiType(BindingXmlReader reader, Class<?> declared, NamespaceFrame scope) {
        String lexical=attribute(reader,new XmlExpandedName("http://www.w3.org/2001/XMLSchema-instance","type"),scope);
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

    private Object readProperty(BindingXmlReader reader,XmlBindingMetadata.Property property,NamespaceFrame scope){
        if(isNil(reader,scope)){skipElement(reader);return null;}
        if(property.hexBinary()||XmlBindingMetadata.scalar(property.xmlType())){
            String text=readElementText(reader);if(text.isEmpty()&&property.defaultValue()!=null)text=property.defaultValue();
            if(property.xmlType()==javax.xml.namespace.QName.class)return scope.resolveQName(text);
            return property.convert(text);
        }
        return readObject(reader,property.xmlType(),scope);
    }

    private static boolean isNil(BindingXmlReader reader,NamespaceFrame scope){
        String value=attribute(reader,new XmlExpandedName("http://www.w3.org/2001/XMLSchema-instance","nil"),scope);
        return "true".equals(value)||"1".equals(value);
    }

    private Object unmarshalAdapted(XmlBindingMetadata.Property property, Object value) {
        if (property.adapterType() == null) return value;
        XmlBindingAdapter adapter = adapters.get(property.adapterType());
        if (adapter == null) throw new XmlBindingException("No XmlAdapter registered for " + property.adapterType().getName());
        try { return adapter.unmarshal(value); }
        catch (Exception failure) { throw new XmlBindingException("XmlAdapter unmarshal failed", failure); }
    }

    private static String readElementText(BindingXmlReader reader) {
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.TEXT || event == XmlEvent.CDATA) text.append(reader.text());
            else if (event == XmlEvent.START_ELEMENT)
                throw new XmlBindingException("Scalar element contains child <" + reader.name() + ">");
            else if (event == XmlEvent.END_ELEMENT) return text.toString();
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
            int count = 0;
            for (String name : reader.attributes().keySet())
                if (name.equals("xmlns") || name.startsWith("xmlns:")) count++;
            if (count == 0) return parent == null ? EMPTY : parent;
            String[] prefixes = new String[count], uris = new String[count];
            int index = 0;
            for (Map.Entry<String, String> entry : reader.attributes().entrySet()) {
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
