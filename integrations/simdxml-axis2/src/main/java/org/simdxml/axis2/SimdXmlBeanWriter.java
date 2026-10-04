package org.simdxml.axis2;

import org.simdxml.SimdJaxbContext;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/** Public Axis2-facing bean codec backed by simdxml's JAXB-neutral API. */
public final class SimdXmlBeanWriter<T> {
    private final Class<T> beanClass;
    private final SimdJaxbContext context;

    public SimdXmlBeanWriter(Class<T> beanClass) {
        this.beanClass = java.util.Objects.requireNonNull(beanClass, "beanClass");
        this.context = SimdJaxbContext.newInstance(beanClass);
    }

    public Class<T> beanClass() { return beanClass; }
    public byte[] serialize(T bean) { return context.createMarshaller().marshal(bean); }
    public void serialize(T bean, XMLStreamWriter writer) { context.createMarshaller().marshal(bean, writer); }
    public void serialize(T bean, XMLEventWriter writer) { context.createMarshaller().marshal(bean, writer); }
    public T populate(byte[] xml) { return context.unmarshal(xml, beanClass); }
    public T populate(XMLStreamReader reader) { return context.createUnmarshaller().unmarshal(reader, beanClass); }
}
