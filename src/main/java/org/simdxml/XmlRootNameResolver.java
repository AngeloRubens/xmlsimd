package org.simdxml;

import javax.xml.namespace.QName;
import java.util.Map;

/** Resolves a document element to a namespace-aware QName without building a tree. */
public final class XmlRootNameResolver {
    public static QName resolve(byte[] xml) {
        SimdXmlStreamReader reader = new SimdXmlParser(Math.max(1, xml.length), 64).stream(xml);
        while (reader.hasNext()) {
            if (reader.next() != XmlEvent.START_ELEMENT) continue;
            String qualified = reader.name();
            int colon = qualified.indexOf(':');
            String prefix = colon < 0 ? "" : qualified.substring(0, colon);
            String local = colon < 0 ? qualified : qualified.substring(colon + 1);
            Map<String, String> attributes = reader.attributes();
            String namespace = attributes.get(prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix);
            if (!prefix.isEmpty() && namespace == null)
                throw new XmlBindingException("Unbound XML namespace prefix: " + prefix);
            return new QName(namespace == null ? "" : namespace, local, prefix);
        }
        throw new XmlBindingException("Document has no root element");
    }

    private XmlRootNameResolver() { }
}
