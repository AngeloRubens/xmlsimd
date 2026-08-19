package org.simdxml;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adapts standard StAX cursors directly to the simdxml binder event contract. */
final class StaxBindingReaders {
    static BindingXmlReader cursor(XMLStreamReader reader) { return new Cursor(reader); }
    static BindingXmlReader events(XMLEventReader reader) { return new Events(reader); }

    private abstract static class Base implements BindingXmlReader {
        XmlEvent event; String name, text; Map<String,String> attributes = java.util.Collections.emptyMap();
        boolean ended;
        @Override public boolean hasNext() { return !ended; }
        @Override public String name() { return name; }
        @Override public String text() { return text; }
        @Override public Map<String,String> attributes() { return attributes; }
        @Override public String attribute(String key) { return attributes.get(key); }
        @Override public boolean hasNamespaceDeclarations() {
            for (String key : attributes.keySet()) if (key.equals("xmlns") || key.startsWith("xmlns:")) return true;
            return false;
        }
        static String qualified(QName name) { return name.getPrefix()==null||name.getPrefix().isEmpty()?name.getLocalPart():name.getPrefix()+":"+name.getLocalPart(); }
        static XmlEvent map(int type) {
            switch (type) {
                case XMLStreamConstants.START_DOCUMENT: return XmlEvent.START_DOCUMENT;
                case XMLStreamConstants.END_DOCUMENT: return XmlEvent.END_DOCUMENT;
                case XMLStreamConstants.START_ELEMENT: return XmlEvent.START_ELEMENT;
                case XMLStreamConstants.END_ELEMENT: return XmlEvent.END_ELEMENT;
                case XMLStreamConstants.CHARACTERS: case XMLStreamConstants.SPACE: return XmlEvent.TEXT;
                case XMLStreamConstants.CDATA: return XmlEvent.CDATA;
                case XMLStreamConstants.COMMENT: return XmlEvent.COMMENT;
                case XMLStreamConstants.PROCESSING_INSTRUCTION: return XmlEvent.PROCESSING_INSTRUCTION;
                default: return null;
            }
        }
    }
    private static final class Cursor extends Base {
        private final XMLStreamReader reader; private boolean first=true;
        Cursor(XMLStreamReader reader) { this.reader=reader; }
        @Override public XmlEvent next() {
            try {
                while (true) {
                    int type;
                    if (first) { first=false; type=reader.getEventType(); }
                    else { if (!reader.hasNext()) { ended=true; return event=XmlEvent.END_DOCUMENT; } type=reader.next(); }
                    XmlEvent mapped=map(type); if(mapped==null) continue;
                    load(type); if(mapped==XmlEvent.END_DOCUMENT) ended=true; return event=mapped;
                }
            } catch (XMLStreamException e) { throw new XmlBindingException("Cannot read StAX cursor",e); }
        }
        private void load(int type) {
            name=null;text=null;attributes=java.util.Collections.emptyMap();
            if(type==XMLStreamConstants.START_ELEMENT||type==XMLStreamConstants.END_ELEMENT) name=qualified(reader.getName());
            if(type==XMLStreamConstants.CHARACTERS||type==XMLStreamConstants.SPACE||type==XMLStreamConstants.CDATA||type==XMLStreamConstants.COMMENT) text=reader.getText();
            if(type==XMLStreamConstants.START_ELEMENT){ LinkedHashMap<String,String> map=new LinkedHashMap<String,String>(); for(int i=0;i<reader.getNamespaceCount();i++){String p=reader.getNamespacePrefix(i);map.put(p==null||p.isEmpty()?"xmlns":"xmlns:"+p,reader.getNamespaceURI(i));} for(int i=0;i<reader.getAttributeCount();i++)map.put(qualified(reader.getAttributeName(i)),reader.getAttributeValue(i));attributes=map; }
        }
    }
    private static final class Events extends Base {
        private final XMLEventReader reader;
        Events(XMLEventReader reader){this.reader=reader;}
        @Override public XmlEvent next(){ try { while(reader.hasNext()){XMLEvent x=reader.nextEvent();XmlEvent mapped=map(x.getEventType());if(mapped==null)continue;load(x);if(mapped==XmlEvent.END_DOCUMENT)ended=true;return event=mapped;} ended=true;return event=XmlEvent.END_DOCUMENT;}catch(XMLStreamException e){throw new XmlBindingException("Cannot read StAX events",e);} }
        private void load(XMLEvent x){name=null;text=null;attributes=java.util.Collections.emptyMap();if(x.isStartElement()){name=qualified(x.asStartElement().getName());LinkedHashMap<String,String> map=new LinkedHashMap<String,String>();java.util.Iterator<?> ns=x.asStartElement().getNamespaces();while(ns.hasNext()){javax.xml.stream.events.Namespace n=(javax.xml.stream.events.Namespace)ns.next();map.put(n.isDefaultNamespaceDeclaration()?"xmlns":"xmlns:"+n.getPrefix(),n.getNamespaceURI());}java.util.Iterator<?> as=x.asStartElement().getAttributes();while(as.hasNext()){javax.xml.stream.events.Attribute a=(javax.xml.stream.events.Attribute)as.next();map.put(qualified(a.getName()),a.getValue());}attributes=map;}else if(x.isEndElement())name=qualified(x.asEndElement().getName());else if(x.isCharacters())text=x.asCharacters().getData();}
    }
    private StaxBindingReaders() { }
}
