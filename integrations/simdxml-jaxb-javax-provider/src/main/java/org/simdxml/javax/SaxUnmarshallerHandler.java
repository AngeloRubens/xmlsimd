package org.simdxml.javax;

import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshallerHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/** Java EE 8 facade over the native push SAX object binder. */
final class SaxUnmarshallerHandler implements UnmarshallerHandler {
    private final org.simdxml.SimdUnmarshaller unmarshaller;
    private final ContentHandler delegate;
    private final ContentHandler resultHandler;
    private final javax.xml.bind.Unmarshaller.Listener listener;
    SaxUnmarshallerHandler(org.simdxml.SimdUnmarshaller unmarshaller,javax.xml.validation.Schema schema,javax.xml.bind.Unmarshaller.Listener listener){this.unmarshaller=unmarshaller;this.listener=listener;resultHandler=unmarshaller.saxHandler();if(schema==null)delegate=resultHandler;else{javax.xml.validation.ValidatorHandler validator=schema.newValidatorHandler();validator.setContentHandler(resultHandler);delegate=validator;}}
    @Override public Object getResult() throws JAXBException{try{return unmarshaller.saxResult(resultHandler);}catch(RuntimeException e){throw new JAXBException(e);}}
    @Override public void setDocumentLocator(Locator locator){delegate.setDocumentLocator(locator);}
    @Override public void startDocument()throws SAXException{if(listener!=null)listener.beforeUnmarshal(null,null);delegate.startDocument();}
    @Override public void endDocument()throws SAXException{delegate.endDocument();if(listener!=null)listener.afterUnmarshal(unmarshaller.saxResult(resultHandler),null);}
    @Override public void startPrefixMapping(String prefix,String uri)throws SAXException{delegate.startPrefixMapping(prefix,uri);}
    @Override public void endPrefixMapping(String prefix)throws SAXException{delegate.endPrefixMapping(prefix);}
    @Override public void startElement(String uri,String local,String qName,Attributes attributes)throws SAXException{delegate.startElement(uri,local,qName,attributes);}
    @Override public void endElement(String uri,String local,String qName)throws SAXException{delegate.endElement(uri,local,qName);}
    @Override public void characters(char[] ch,int start,int length)throws SAXException{delegate.characters(ch,start,length);}
    @Override public void ignorableWhitespace(char[] ch,int start,int length)throws SAXException{delegate.ignorableWhitespace(ch,start,length);}
    @Override public void processingInstruction(String target,String data)throws SAXException{delegate.processingInstruction(target,data);}
    @Override public void skippedEntity(String name)throws SAXException{delegate.skippedEntity(name);}
}
