package org.simdxml;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Zero-text-buffer event adapters used by the native binding traversal. */
final class EventXmlStreamWriters {
    static XMLStreamWriter events(XMLEventWriter target) { return new EventWriter(target); }
    static XMLStreamWriter sax(ContentHandler target) { return new SaxWriter(target); }

    private abstract static class PendingWriter implements XMLStreamWriter {
        final ArrayDeque<QName> elements = new ArrayDeque<QName>();
        QName pending;
        final List<Attribute> attributes = new ArrayList<Attribute>();
        final List<Namespace> namespaces = new ArrayList<Namespace>();
        final XMLEventFactory factory = XMLEventFactory.newFactory();
        abstract void emitStart(QName name, List<Attribute> attributes, List<Namespace> namespaces) throws Exception;
        abstract void emitEnd(QName name) throws Exception;
        abstract void emitCharacters(String text) throws Exception;
        abstract void emitStartDocument(String encoding, String version) throws Exception;
        abstract void emitEndDocument() throws Exception;
        abstract void emitFlush() throws Exception;
        final void flushPending() throws XMLStreamException {
            if (pending == null) return;
            try { emitStart(pending, attributes, namespaces); elements.push(pending); pending = null; attributes.clear(); namespaces.clear(); }
            catch (Exception failure) { throw stream(failure); }
        }
        @Override public void writeStartElement(String local) throws XMLStreamException { start("", local, ""); }
        @Override public void writeStartElement(String namespace, String local) throws XMLStreamException { start("", local, namespace); }
        @Override public void writeStartElement(String prefix, String local, String namespace) throws XMLStreamException { start(prefix, local, namespace); }
        private void start(String prefix, String local, String namespace) throws XMLStreamException {
            flushPending(); pending = new QName(namespace == null ? "" : namespace, local, prefix == null ? "" : prefix);
        }
        @Override public void writeEmptyElement(String namespace, String local) throws XMLStreamException { writeStartElement(namespace, local); writeEndElement(); }
        @Override public void writeEmptyElement(String prefix, String local, String namespace) throws XMLStreamException { writeStartElement(prefix, local, namespace); writeEndElement(); }
        @Override public void writeEmptyElement(String local) throws XMLStreamException { writeStartElement(local); writeEndElement(); }
        @Override public void writeEndElement() throws XMLStreamException { flushPending(); try { emitEnd(elements.pop()); } catch (Exception e) { throw stream(e); } }
        @Override public void writeAttribute(String local, String value) { attributes.add(factory.createAttribute(local, value)); }
        @Override public void writeAttribute(String prefix, String namespace, String local, String value) { attributes.add(factory.createAttribute(prefix, namespace, local, value)); }
        @Override public void writeAttribute(String namespace, String local, String value) { attributes.add(factory.createAttribute("", namespace, local, value)); }
        @Override public void writeNamespace(String prefix, String namespace) { namespaces.add(factory.createNamespace(prefix, namespace)); }
        @Override public void writeDefaultNamespace(String namespace) { namespaces.add(factory.createNamespace(namespace)); }
        @Override public void writeCharacters(String text) throws XMLStreamException { flushPending(); try { emitCharacters(text); } catch (Exception e) { throw stream(e); } }
        @Override public void writeCharacters(char[] text, int start, int len) throws XMLStreamException { writeCharacters(new String(text, start, len)); }
        @Override public void writeStartDocument() throws XMLStreamException { writeStartDocument("UTF-8", "1.0"); }
        @Override public void writeStartDocument(String version) throws XMLStreamException { writeStartDocument("UTF-8", version); }
        @Override public void writeStartDocument(String encoding, String version) throws XMLStreamException { try { emitStartDocument(encoding, version); } catch (Exception e) { throw stream(e); } }
        @Override public void writeEndDocument() throws XMLStreamException { flushPending(); try { emitEndDocument(); } catch (Exception e) { throw stream(e); } }
        @Override public void flush() throws XMLStreamException { flushPending(); try { emitFlush(); } catch (Exception e) { throw stream(e); } }
        @Override public void close() throws XMLStreamException { flush(); }
        @Override public void writeComment(String data) throws XMLStreamException { throw unsupported(); }
        @Override public void writeProcessingInstruction(String target) throws XMLStreamException { throw unsupported(); }
        @Override public void writeProcessingInstruction(String target, String data) throws XMLStreamException { throw unsupported(); }
        @Override public void writeCData(String data) throws XMLStreamException { writeCharacters(data); }
        @Override public void writeDTD(String dtd) throws XMLStreamException { throw unsupported(); }
        @Override public void writeEntityRef(String name) throws XMLStreamException { throw unsupported(); }
        @Override public String getPrefix(String uri) { return null; }
        @Override public void setPrefix(String prefix, String uri) { }
        @Override public void setDefaultNamespace(String uri) { }
        @Override public void setNamespaceContext(NamespaceContext context) { }
        @Override public NamespaceContext getNamespaceContext() { return null; }
        @Override public Object getProperty(String name) { throw new IllegalArgumentException(name); }
        private static XMLStreamException stream(Exception e) { return e instanceof XMLStreamException ? (XMLStreamException) e : new XMLStreamException(e); }
        private static XMLStreamException unsupported() { return new XMLStreamException("Operation is not used by simdxml binding traversal"); }
    }

    private static final class EventWriter extends PendingWriter {
        private final XMLEventWriter target;
        EventWriter(XMLEventWriter target) { this.target = target; }
        @Override void emitStart(QName n, List<Attribute> a, List<Namespace> ns) throws Exception { target.add(factory.createStartElement(n, a.iterator(), ns.iterator())); }
        @Override void emitEnd(QName n) throws Exception { target.add(factory.createEndElement(n, java.util.Collections.<Namespace>emptyList().iterator())); }
        @Override void emitCharacters(String text) throws Exception { target.add(factory.createCharacters(text)); }
        @Override void emitStartDocument(String encoding, String version) throws Exception { target.add(factory.createStartDocument(encoding, version)); }
        @Override void emitEndDocument() throws Exception { target.add(factory.createEndDocument()); }
        @Override void emitFlush() throws Exception { target.flush(); }
    }

    private static final class SaxWriter extends PendingWriter {
        private static final class Frame { final QName name; final String[] prefixes; Frame(QName n, String[] p) { name=n; prefixes=p; } }
        private final ContentHandler target;
        private final ArrayDeque<Frame> frames = new ArrayDeque<Frame>();
        SaxWriter(ContentHandler target) { this.target = target; }
        @Override void emitStart(QName n, List<Attribute> attrs, List<Namespace> ns) throws SAXException {
            String[] prefixes = new String[ns.size()];
            for (int i=0;i<ns.size();i++) { Namespace x=ns.get(i); prefixes[i]=x.getPrefix(); target.startPrefixMapping(prefixes[i], x.getNamespaceURI()); }
            AttributesImpl saxAttrs = new AttributesImpl();
            for (Attribute a : attrs) { QName q=a.getName(); saxAttrs.addAttribute(q.getNamespaceURI(),q.getLocalPart(),qualified(q),"CDATA",a.getValue()); }
            target.startElement(n.getNamespaceURI(), n.getLocalPart(), qualified(n), saxAttrs); frames.push(new Frame(n,prefixes));
        }
        @Override void emitEnd(QName ignored) throws SAXException { Frame f=frames.pop(); target.endElement(f.name.getNamespaceURI(),f.name.getLocalPart(),qualified(f.name)); for(int i=f.prefixes.length-1;i>=0;i--) target.endPrefixMapping(f.prefixes[i]); }
        @Override void emitCharacters(String text) throws SAXException { char[] chars=text.toCharArray(); target.characters(chars,0,chars.length); }
        @Override void emitStartDocument(String encoding, String version) throws SAXException { target.startDocument(); }
        @Override void emitEndDocument() throws SAXException { target.endDocument(); }
        @Override void emitFlush() { }
        private static String qualified(QName n) { return n.getPrefix().isEmpty()?n.getLocalPart():n.getPrefix()+":"+n.getLocalPart(); }
    }
    private EventXmlStreamWriters() { }
}
