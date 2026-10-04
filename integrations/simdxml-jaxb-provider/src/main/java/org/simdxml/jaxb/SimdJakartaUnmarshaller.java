package org.simdxml.jaxb;

import jakarta.xml.bind.*;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.attachment.AttachmentUnmarshaller;
import org.simdxml.SimdJaxbContext;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class SimdJakartaUnmarshaller implements Unmarshaller {
    private final SimdJaxbContext delegate;
    private final org.simdxml.SimdUnmarshaller coreUnmarshaller;
    private ValidationEventHandler handler;
    private Schema schema;
    private AttachmentUnmarshaller attachments;
    private Listener listener;
    private final java.util.Map<Class<?>, XmlAdapter<?, ?>> adapters = new java.util.HashMap<Class<?>, XmlAdapter<?, ?>>();

    SimdJakartaUnmarshaller(SimdJaxbContext delegate, Set<Class<?>> types) {
        this.delegate = delegate;
        this.coreUnmarshaller = delegate.createUnmarshaller();
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) void installDefaultAdapter(Class<?> type) {
        try { setAdapter((Class) type, (XmlAdapter) type.getDeclaredConstructor().newInstance()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot initialize XmlAdapter " + type.getName(), failure); }
    }

    @Override public Object unmarshal(InputStream input) throws JAXBException { return global(read(input)); }
    @Override public Object unmarshal(Reader input) throws JAXBException { return global(read(input)); }
    @Override public Object unmarshal(File input) throws JAXBException {
        try (InputStream stream = new FileInputStream(input)) { return unmarshal(stream); }
        catch (IOException e) { throw new JAXBException(e); }
    }
    @Override public Object unmarshal(URL input) throws JAXBException {
        try (InputStream stream = input.openStream()) { return unmarshal(stream); }
        catch (IOException e) { throw new JAXBException(e); }
    }
    @Override public Object unmarshal(org.xml.sax.InputSource source) throws JAXBException {
        if (source.getByteStream() != null) return unmarshal(source.getByteStream());
        if (source.getCharacterStream() != null) return unmarshal(source.getCharacterStream());
        try { return unmarshal(new URL(source.getSystemId())); }
        catch (Exception e) { throw new JAXBException(e); }
    }
    @Override public Object unmarshal(Source source) throws JAXBException { return global(sourceBytes(source)); }
    @Override public Object unmarshal(org.w3c.dom.Node node) throws JAXBException { return unmarshal(new DOMSource(node)); }
    @Override public Object unmarshal(javax.xml.stream.XMLStreamReader reader) throws JAXBException {
        if (schema != null) return unmarshal(staxSource(reader));
        try { if(listener!=null)listener.beforeUnmarshal(null,null); Object value=configured().unmarshal(reader); if(listener!=null)listener.afterUnmarshal(value,null); return value; }
        catch (RuntimeException failure) { throw new UnmarshalException(failure); }
    }
    @Override public Object unmarshal(javax.xml.stream.XMLEventReader reader) throws JAXBException {
        if (schema != null) return unmarshal(staxSource(reader));
        try { if(listener!=null)listener.beforeUnmarshal(null,null); Object value=configured().unmarshal(reader); if(listener!=null)listener.afterUnmarshal(value,null); return value; }
        catch (RuntimeException failure) { throw new UnmarshalException(failure); }
    }
    @Override public <T> JAXBElement<T> unmarshal(Source source, Class<T> type) throws JAXBException {
        return declared(sourceBytes(source), type);
    }
    @Override public <T> JAXBElement<T> unmarshal(org.w3c.dom.Node node, Class<T> type) throws JAXBException {
        return unmarshal(new DOMSource(node), type);
    }
    @Override public <T> JAXBElement<T> unmarshal(javax.xml.stream.XMLStreamReader reader, Class<T> type) throws JAXBException {
        if (schema != null) return unmarshal(staxSource(reader), type);
        try {
            javax.xml.namespace.QName name = staxRoot(reader);
            return new JAXBElement<T>(name, type, configured().unmarshal(reader, type));
        } catch (RuntimeException failure) { throw new UnmarshalException(failure); }
    }
    @Override public <T> JAXBElement<T> unmarshal(javax.xml.stream.XMLEventReader reader, Class<T> type) throws JAXBException {
        if (schema != null) return unmarshal(staxSource(reader), type);
        try {
            javax.xml.namespace.QName name = staxRoot(reader);
            return new JAXBElement<T>(name, type, configured().unmarshal(reader, type));
        } catch (javax.xml.stream.XMLStreamException failure) { throw new UnmarshalException(failure); }
        catch (RuntimeException failure) { throw new UnmarshalException(failure); }
    }
    private static javax.xml.namespace.QName staxRoot(javax.xml.stream.XMLStreamReader reader) throws JAXBException {
        try { while (!reader.isStartElement() && reader.hasNext()) reader.next(); return reader.getName(); }
        catch (javax.xml.stream.XMLStreamException failure) { throw new JAXBException(failure); }
    }
    private static javax.xml.namespace.QName staxRoot(javax.xml.stream.XMLEventReader reader) throws javax.xml.stream.XMLStreamException {
        while (reader.hasNext() && !reader.peek().isStartElement()) reader.nextEvent();
        return reader.peek().asStartElement().getName();
    }

    private Object global(byte[] xml) throws JAXBException {
        try {
            validate(xml);
            if (listener != null) listener.beforeUnmarshal(null, null);
            Object value = configured().unmarshal(xml);
            if (listener != null) listener.afterUnmarshal(value, null);
            return value;
        } catch (RuntimeException e) { throw new UnmarshalException(e); }
    }
    private <T> JAXBElement<T> declared(byte[] xml, Class<T> type) throws JAXBException {
        try {
            validate(xml);
            T value = configured().unmarshal(xml, type);
            return new JAXBElement<>(org.simdxml.XmlRootNameResolver.resolve(xml), type, value);
        } catch (RuntimeException e) { throw new UnmarshalException(e); }
    }
    private void validate(byte[] xml) throws JAXBException {
        if (schema == null) return;
        try { schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml))); }
        catch (Exception failure) { throw new UnmarshalException("XML schema validation failed", failure); }
    }
    private static String rootName(Class<?> type) {
        XmlRootElement root = type.getAnnotation(XmlRootElement.class);
        return root == null || root.name().equals("##default") ? type.getSimpleName() : root.name();
    }
    private static byte[] read(InputStream input) throws JAXBException {
        try { return input.readAllBytes(); } catch (IOException e) { throw new JAXBException(e); }
    }
    private static byte[] read(Reader input) throws JAXBException {
        try {
            StringBuilder text = new StringBuilder(); char[] buffer = new char[8192]; int count;
            while ((count = input.read(buffer)) >= 0) text.append(buffer, 0, count);
            return text.toString().getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) { throw new JAXBException(e); }
    }
    private static byte[] sourceBytes(Source source) throws JAXBException {
        if (source instanceof StreamSource stream) {
            if (stream.getInputStream() != null) return read(stream.getInputStream());
            if (stream.getReader() != null) return read(stream.getReader());
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer().transform(source, new StreamResult(output));
            return output.toByteArray();
        } catch (Exception e) { throw new JAXBException(e); }
    }
    private static StAXSource staxSource(javax.xml.stream.XMLStreamReader reader) throws JAXBException {
        return new StAXSource(reader);
    }
    private static StAXSource staxSource(javax.xml.stream.XMLEventReader reader) throws JAXBException {
        try { return new StAXSource(reader); }
        catch (javax.xml.stream.XMLStreamException e) { throw new JAXBException(e); }
    }
    @Override public void setEventHandler(ValidationEventHandler value) { handler = value; }
    @Override public ValidationEventHandler getEventHandler() { return handler; }
    @Override public void setSchema(Schema value) { schema = value; }
    @Override public Schema getSchema() { return schema; }
    @Override public void setAttachmentUnmarshaller(AttachmentUnmarshaller value) { attachments = value; }
    @Override public AttachmentUnmarshaller getAttachmentUnmarshaller() { return attachments; }
    private org.simdxml.SimdUnmarshaller configured() { return coreUnmarshaller.withAttachmentHandler(JakartaAttachmentBridge.unmarshaller(attachments)); }
    @Override public void setListener(Listener value) { listener = value; }
    @Override public Listener getListener() { return listener; }
    @Override public void setProperty(String name, Object value) throws PropertyException { throw new PropertyException(name, value); }
    @Override public Object getProperty(String name) throws PropertyException { throw new PropertyException(name); }
    @Override public <A extends XmlAdapter<?, ?>> void setAdapter(A adapter) {
        if (adapter == null) throw new IllegalArgumentException("adapter");
        @SuppressWarnings("unchecked") Class<A> type = (Class<A>) adapter.getClass();
        setAdapter(type, adapter);
    }
    @Override public <A extends XmlAdapter<?, ?>> void setAdapter(Class<A> type, final A adapter) {
        if (type == null || adapter == null) throw new IllegalArgumentException("adapter");
        adapters.put(type, adapter);
        this.delegateAdapter(type, adapter);
    }
    private <A extends XmlAdapter<?, ?>> void delegateAdapter(Class<A> type, final A adapter) {
        // The reusable core unmarshaller is owned by this facade; see constructor field below.
        coreUnmarshaller.withAdapter(type, new org.simdxml.XmlBindingAdapter() {
            @Override public Object marshal(Object value) { throw new UnsupportedOperationException(); }
            @Override public Object unmarshal(Object value) throws Exception { return unmarshalValue(adapter, value); }
        });
    }
    @SuppressWarnings("unchecked")
    private static Object unmarshalValue(XmlAdapter adapter, Object value) throws Exception { return adapter.unmarshal(value); }
    @Override @SuppressWarnings("unchecked") public <A extends XmlAdapter<?, ?>> A getAdapter(Class<A> type) { return (A) adapters.get(type); }
    @Override public UnmarshallerHandler getUnmarshallerHandler() {
        return new SaxUnmarshallerHandler(configured(), schema, listener);
    }
}
