package org.simdxml.javax;

import javax.xml.bind.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.attachment.AttachmentMarshaller;
import org.simdxml.SimdMarshaller;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import java.io.*;

final class SimdJakartaMarshaller implements Marshaller {
    private final SimdMarshaller delegate;
    private boolean fragment;
    private ValidationEventHandler handler;
    private Listener listener;
    private Schema schema;
    private AttachmentMarshaller attachments;
    private final java.util.Map<Class<?>, XmlAdapter> adapters = new java.util.HashMap<Class<?>, XmlAdapter>();
    SimdJakartaMarshaller(SimdMarshaller delegate) { this.delegate = delegate; }
    @SuppressWarnings({"rawtypes", "unchecked"}) void installDefaultAdapter(Class<?> type) {
        try { setAdapterRaw((Class) type, (XmlAdapter) type.getDeclaredConstructor().newInstance()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot initialize XmlAdapter " + type.getName(), failure); }
    }
    @Override public void marshal(Object value, OutputStream output) throws JAXBException { run(value, output); }
    @Override public void marshal(Object value, File file) throws JAXBException {
        try (OutputStream output = new FileOutputStream(file)) { run(value, output); }
        catch (IOException e) { throw new JAXBException(e); }
    }
    @Override public void marshal(Object value, Writer output) throws JAXBException {
        try { output.write(new String(bytes(value), java.nio.charset.StandardCharsets.UTF_8)); output.flush(); }
        catch (IOException e) { throw new JAXBException(e); }
    }
    @Override public void marshal(Object value, Result result) throws JAXBException {
        if (result instanceof StreamResult) {
            StreamResult stream = (StreamResult) result;
            if (stream.getOutputStream() != null) { run(value, stream.getOutputStream()); return; }
            if (stream.getWriter() != null) { marshal(value, stream.getWriter()); return; }
        }
        transform(value, result);
    }
    private byte[] bytes(Object value) throws JAXBException {
        byte[] xml;
        if (value instanceof JAXBElement) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            marshalElement((JAXBElement) value, output);
            xml = output.toByteArray();
        } else {
            try { xml = configured().withXmlDeclaration(!fragment).marshal(value); }
            catch (RuntimeException e) { throw new JAXBException(e); }
        }
        validate(xml);
        return xml;
    }
    private void run(Object value, OutputStream output) throws JAXBException {
        if (schema != null) {
            try { output.write(bytes(value)); }
            catch (IOException failure) { throw new JAXBException(failure); }
            return;
        }
        if (value instanceof JAXBElement) {
            marshalElement((JAXBElement) value, output);
            return;
        }
        try {
            if (listener != null) listener.beforeMarshal(value);
            configured().withXmlDeclaration(!fragment).marshal(value, output);
            if (listener != null) listener.afterMarshal(value);
        } catch (RuntimeException e) { throw new JAXBException(e); }
    }
    private void validate(byte[] xml) throws JAXBException {
        if (schema == null) return;
        try { schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml))); }
        catch (Exception failure) { throw new MarshalException("XML schema validation failed", failure); }
    }
    private void marshalElement(JAXBElement element, OutputStream output) throws JAXBException {
        try {
            javax.xml.namespace.QName name = element.getName();
            delegate.withXmlDeclaration(!fragment).marshalElement(
                    element.getValue(), name.getNamespaceURI(), name.getLocalPart(), element.isNil(), output);
        } catch (RuntimeException failure) {
            throw new JAXBException(failure);
        }
    }
    @Override public void setProperty(String name, Object value) throws PropertyException {
        if (JAXB_FRAGMENT.equals(name) && value instanceof Boolean) { fragment = ((Boolean) value).booleanValue(); return; }
        if (JAXB_ENCODING.equals(name) && "UTF-8".equalsIgnoreCase(String.valueOf(value))) return;
        if (JAXB_FORMATTED_OUTPUT.equals(name) && Boolean.FALSE.equals(value)) return;
        throw new PropertyException(name, value);
    }
    @Override public Object getProperty(String name) throws PropertyException {
        if (JAXB_FRAGMENT.equals(name)) return fragment;
        if (JAXB_ENCODING.equals(name)) return "UTF-8";
        if (JAXB_FORMATTED_OUTPUT.equals(name)) return false;
        throw new PropertyException(name);
    }
    @Override public void setEventHandler(ValidationEventHandler value) { handler = value; }
    @Override public ValidationEventHandler getEventHandler() { return handler; }
    @Override public void setSchema(Schema value) { schema = value; }
    @Override public Schema getSchema() { return schema; }
    @Override public void setListener(Listener value) { listener = value; }
    @Override public Listener getListener() { return listener; }
    @Override public void setAttachmentMarshaller(AttachmentMarshaller value) { attachments = value; }
    @Override public AttachmentMarshaller getAttachmentMarshaller() { return attachments; }
    private SimdMarshaller configured() { return delegate.withAttachmentHandler(JavaxAttachmentBridge.marshaller(attachments)); }
    @Override public void setAdapter(XmlAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("adapter");
        setAdapterRaw(adapter.getClass(), adapter);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setAdapterRaw(Class type, XmlAdapter adapter) { setAdapter(type, adapter); }
    @Override public <A extends XmlAdapter> void setAdapter(Class<A> type, final A adapter) {
        if (type == null || adapter == null) throw new IllegalArgumentException("adapter");
        adapters.put(type, adapter);
        delegate.withAdapter(type, new org.simdxml.XmlBindingAdapter() {
            @Override public Object marshal(Object value) throws Exception { return adapter.marshal(value); }
            @Override public Object unmarshal(Object value) { throw new UnsupportedOperationException(); }
        });
    }
    @Override @SuppressWarnings("unchecked") public <A extends XmlAdapter> A getAdapter(Class<A> type) { return (A) adapters.get(type); }
    @Override public void marshal(Object v, org.xml.sax.ContentHandler h) throws JAXBException {
        if (schema != null) { transform(v, new SAXResult(h)); return; }
        directEventMarshal(v, h, null);
    }
    @Override public void marshal(Object v, org.w3c.dom.Node n) throws JAXBException { transform(v, new DOMResult(n)); }
    @Override public void marshal(Object v, javax.xml.stream.XMLStreamWriter w) throws JAXBException {
        if (schema != null) { transform(v, new StAXResult(w)); return; }
        try {
            if (listener != null) listener.beforeMarshal(v);
            if (v instanceof JAXBElement) {
                JAXBElement element = (JAXBElement) v;
                javax.xml.namespace.QName name = element.getName();
                configured().withXmlDeclaration(!fragment).marshalElement(element.getValue(), name.getNamespaceURI(),
                        name.getLocalPart(), element.isNil(), w);
            } else configured().withXmlDeclaration(!fragment).marshal(v, w);
            if (listener != null) listener.afterMarshal(v);
        } catch (RuntimeException failure) { throw new JAXBException(failure); }
    }
    @Override public void marshal(Object v, javax.xml.stream.XMLEventWriter w) throws JAXBException {
        if (schema != null) { transform(v, new StAXResult(w)); return; }
        directEventMarshal(v, null, w);
    }
    private void directEventMarshal(Object value, org.xml.sax.ContentHandler sax,
            javax.xml.stream.XMLEventWriter events) throws JAXBException {
        try {
            if (listener != null) listener.beforeMarshal(value);
            if (value instanceof JAXBElement) {
                JAXBElement element=(JAXBElement)value; javax.xml.namespace.QName name=element.getName();
                if (sax != null) configured().withXmlDeclaration(!fragment).marshalElement(element.getValue(), name.getNamespaceURI(), name.getLocalPart(), element.isNil(), sax);
                else configured().withXmlDeclaration(!fragment).marshalElement(element.getValue(), name.getNamespaceURI(), name.getLocalPart(), element.isNil(), events);
            } else if (sax != null) configured().withXmlDeclaration(!fragment).marshal(value, sax);
            else configured().withXmlDeclaration(!fragment).marshal(value, events);
            if (listener != null) listener.afterMarshal(value);
        } catch (RuntimeException failure) { throw new JAXBException(failure); }
    }
    @Override public org.w3c.dom.Node getNode(Object value) throws JAXBException {
        try {
            org.w3c.dom.Document document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().newDocument();
            marshal(value, document);
            return document.getDocumentElement();
        } catch (javax.xml.parsers.ParserConfigurationException failure) { throw new JAXBException(failure); }
    }
    private void transform(Object value, Result result) throws JAXBException {
        try {
            TransformerFactory.newInstance().newTransformer().transform(
                    new StreamSource(new ByteArrayInputStream(bytes(value))), result);
        } catch (Exception failure) { throw new JAXBException(failure); }
    }
    private static JAXBException unsupported(String feature) { return new JAXBException(feature + " is not implemented"); }
}
