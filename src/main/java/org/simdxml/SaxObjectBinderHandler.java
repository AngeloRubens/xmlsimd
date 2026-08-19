package org.simdxml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Push SAX-to-object binder: no XML reconstruction, document buffer, or second parse. */
final class SaxObjectBinderHandler extends DefaultHandler {
    private final Map<XmlExpandedName, Class<?>> roots;
    private final Map<Class<?>, XmlBindingAdapter> adapters;
    private final ArrayDeque<Frame> stack = new ArrayDeque<Frame>();
    private int skippedDepth;
    private Object result;
    private final Map<String,ArrayDeque<String>> namespaceScopes=new java.util.HashMap<String,ArrayDeque<String>>();

    SaxObjectBinderHandler(Map<XmlExpandedName, Class<?>> roots, Map<Class<?>, XmlBindingAdapter> adapters) {
        this.roots = roots; this.adapters = adapters;
    }

    Object result() { if (result == null) throw new IllegalStateException("SAX document has not completed"); return result; }

    @Override public void startDocument() { stack.clear();namespaceScopes.clear(); skippedDepth=0; result=null; }
    @Override public void startPrefixMapping(String prefix,String uri){String key=prefix==null?"":prefix;ArrayDeque<String> values=namespaceScopes.get(key);if(values==null)namespaceScopes.put(key,values=new ArrayDeque<String>());values.push(uri);}
    @Override public void endPrefixMapping(String prefix){ArrayDeque<String> values=namespaceScopes.get(prefix==null?"":prefix);if(values!=null){values.pop();if(values.isEmpty())namespaceScopes.remove(prefix==null?"":prefix);}}

    @Override public void startElement(String uri, String local, String qName, Attributes attributes) throws SAXException {
        if (skippedDepth != 0) { skippedDepth++; return; }
        String localName = local == null || local.isEmpty() ? local(qName) : local;
        XmlExpandedName name = new XmlExpandedName(uri, localName);
        Class<?> type; XmlBindingMetadata.Property parentProperty = null;
        if (stack.isEmpty()) {
            type = roots.get(name);
            if (type == null) throw new SAXException("No bound class for root element <" + name + ">");
        } else {
            Frame parent=stack.peek();
            if(parent.wrapper){parentProperty=parent.parentProperty;if(!parentProperty.expandedName().equals(name)){skippedDepth=1;return;}}
            else {
                parentProperty=XmlBindingMetadata.children(parent.type).get(name);
                if(parentProperty==null && (uri==null||uri.isEmpty())) parentProperty=XmlBindingMetadata.localChildren(parent.type).get(localName);
                if(parentProperty==null){XmlBindingMetadata.Property wrapped=XmlBindingMetadata.wrappers(parent.type).get(name);if(wrapped!=null){stack.push(Frame.wrapper(wrapped));return;}skippedDepth=1;return;}
            }
            type=parentProperty.xmlType();
        }
        String nilValue=attributes.getValue("http://www.w3.org/2001/XMLSchema-instance","nil");
        if(nilValue==null)nilValue=attributes.getValue("xsi:nil");
        if(nilValue==null)for(int i=0;i<attributes.getLength();i++){
            String attributeLocal=attributes.getLocalName(i),attributeQName=attributes.getQName(i);
            if(("nil".equals(attributeLocal)&&"http://www.w3.org/2001/XMLSchema-instance".equals(attributes.getURI(i)))
                    ||"xsi:nil".equals(attributeQName)){nilValue=attributes.getValue(i);break;}
        }
        type=resolveXsiType(type,attributes);
        Frame frame=new Frame(type,parentProperty,"true".equals(nilValue)||"1".equals(nilValue));
        if(!XmlBindingMetadata.scalar(type)) for(XmlBindingMetadata.Property property:XmlBindingMetadata.attributes(type)){
            String raw=attributes.getValue(property.expandedName().namespace(),property.xmlName());
            if(raw==null && property.expandedName().namespace().isEmpty()) raw=attributes.getValue(property.xmlName());
            if(raw!=null) frame.values[property.index()]=adapt(property,property.xmlType()==javax.xml.namespace.QName.class?resolveQName(raw):property.convert(raw));
        }
        stack.push(frame);
    }

    @Override public void characters(char[] ch,int start,int length){if(skippedDepth==0&&!stack.isEmpty())stack.peek().text.append(ch,start,length);}

    @Override public void endElement(String uri,String local,String qName) throws SAXException {
        if(skippedDepth!=0){if(--skippedDepth==0)return;return;}
        Frame frame=stack.pop(); Object value;
        if(frame.wrapper){Frame parent=stack.peek();parent.values[frame.parentProperty.index()]=frame.items;return;}
        if(frame.nil)value=null;
        else if(XmlBindingMetadata.scalar(frame.type)){String lexical=frame.text.toString();if(lexical.isEmpty()&&frame.parentProperty!=null&&frame.parentProperty.defaultValue()!=null)lexical=frame.parentProperty.defaultValue();value=frame.type==javax.xml.namespace.QName.class?resolveQName(lexical):frame.parentProperty!=null&&frame.parentProperty.hexBinary()
                ?frame.parentProperty.convert(lexical):XmlBindingMetadata.convert(lexical,frame.type);}
        else {
            XmlBindingMetadata.Property text=XmlBindingMetadata.valueProperty(frame.type);
            if(text!=null)frame.values[text.index()]=adapt(text,text.xmlType()==javax.xml.namespace.QName.class?resolveQName(frame.text.toString()):text.convert(frame.text.toString()));
            value=XmlBinder.construct(frame.type,frame.properties,frame.values);
        }
        if(frame.parentProperty!=null&&value!=null)value=adapt(frame.parentProperty,value);
        if(stack.isEmpty()){result=value;return;}
        Frame parent=stack.peek(); XmlBindingMetadata.Property property=frame.parentProperty;
        if(parent.wrapper){parent.items.add(value);return;}
        if(property.list()){
            @SuppressWarnings("unchecked") List<Object> list=(List<Object>)parent.values[property.index()];
            if(list==null)parent.values[property.index()]=list=new ArrayList<Object>(); list.add(value);
        }else parent.values[property.index()]=value;
    }

    private Object adapt(XmlBindingMetadata.Property property,Object value) throws SAXException {
        if(property.adapterType()==null)return value; XmlBindingAdapter adapter=adapters.get(property.adapterType());
        if(adapter==null)throw new SAXException("No XmlAdapter registered for "+property.adapterType().getName());
        try{return adapter.unmarshal(value);}catch(Exception failure){throw new SAXException("XmlAdapter unmarshal failed",failure);}
    }
    private Class<?> resolveXsiType(Class<?> declared,Attributes attributes)throws SAXException{
        String lexical=attributes.getValue("http://www.w3.org/2001/XMLSchema-instance","type");
        if(lexical==null)lexical=attributes.getValue("xsi:type");
        if(lexical==null)return declared;
        javax.xml.namespace.QName name=resolveQName(lexical);Class<?> resolved=XmlBindingMetadata.polymorphicTypes(declared).get(new XmlExpandedName(name.getNamespaceURI(),name.getLocalPart()));
        if(resolved==null||!declared.isAssignableFrom(resolved))throw new SAXException("Unknown xsi:type "+name+" for "+declared.getName());
        return resolved;
    }
    private static String local(String qName){int colon=qName.indexOf(':');return colon<0?qName:qName.substring(colon+1);}
    private javax.xml.namespace.QName resolveQName(String lexical)throws SAXException{String value=lexical.trim();int colon=value.indexOf(':');String prefix=colon<0?"":value.substring(0,colon),local=colon<0?value:value.substring(colon+1);ArrayDeque<String> values=namespaceScopes.get(prefix);String uri=values==null||values.isEmpty()?"":values.peek();if(!prefix.isEmpty()&&uri.isEmpty())throw new SAXException("Unbound QName prefix: "+prefix);return new javax.xml.namespace.QName(uri,local,prefix);}
    private static final class Frame{
        final Class<?> type; final XmlBindingMetadata.Property parentProperty; final List<XmlBindingMetadata.Property> properties;final boolean wrapper,nil;final List<Object> items;
        final Object[] values; final StringBuilder text=new StringBuilder();
        Frame(Class<?> type,XmlBindingMetadata.Property parentProperty){this(type,parentProperty,false);}
        Frame(Class<?> type,XmlBindingMetadata.Property parentProperty,boolean nil){this.type=type;this.parentProperty=parentProperty;this.wrapper=false;this.nil=nil;this.items=null;properties=XmlBindingMetadata.scalar(type)?java.util.Collections.<XmlBindingMetadata.Property>emptyList():XmlBindingMetadata.properties(type);values=new Object[properties.size()];}
        private Frame(XmlBindingMetadata.Property property){type=null;parentProperty=property;wrapper=true;nil=false;items=new ArrayList<Object>();properties=java.util.Collections.emptyList();values=new Object[0];}
        static Frame wrapper(XmlBindingMetadata.Property property){return new Frame(property);}
    }
}
