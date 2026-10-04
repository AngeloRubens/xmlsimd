package org.simdxml.cxf;

import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.service.model.MessagePartInfo;
import org.simdxml.SimdJaxbContext;
import org.simdxml.SimdMarshaller;
import org.simdxml.XmlBindingException;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/** CXF writer backed exclusively by simdxml's public marshalling API. */
public final class SimdXmlDataWriter<T> implements DataWriter<T> {
    SimdXmlDataWriter(Class<T> format) { }

    @Override public void write(Object object, T output) {
        writeValue(object, null, output);
    }

    @Override public void write(Object object, MessagePartInfo part, T output) {
        javax.xml.namespace.QName name = part == null ? null : part.getConcreteName();
        if (name == null && part != null) name = part.getElementQName();
        writeValue(object, name, output);
    }

    private static void writeValue(Object object, javax.xml.namespace.QName elementName, Object output) {
        if (object == null) return;
        SimdMarshaller marshaller = SimdJaxbContext.newInstance(object.getClass()).createMarshaller();
        try {
            if (output instanceof XMLStreamWriter) {
                if (elementName == null) marshaller.marshal(object, (XMLStreamWriter) output);
                else marshaller.marshalElement(object, elementName.getNamespaceURI(), elementName.getLocalPart(), false,
                        (XMLStreamWriter) output);
                return;
            }
            if (output instanceof XMLEventWriter) {
                if (elementName == null) marshaller.marshal(object, (XMLEventWriter) output);
                else marshaller.marshalElement(object, elementName.getNamespaceURI(), elementName.getLocalPart(), false,
                        (XMLEventWriter) output);
                return;
            }
            byte[] xml = marshaller.marshal(object);
            if (output instanceof OutputStream) {
                ((OutputStream) output).write(xml);
                return;
            }
            if (output instanceof Writer) {
                ((Writer) output).write(new String(xml, StandardCharsets.UTF_8));
                return;
            }
            if (output instanceof StreamResult) {
                StreamResult result = (StreamResult) output;
                if (result.getOutputStream() != null) result.getOutputStream().write(xml);
                else if (result.getWriter() != null) result.getWriter().write(new String(xml, StandardCharsets.UTF_8));
                else throw new XmlBindingException("StreamResult has no output stream or writer");
                return;
            }
            if (output instanceof Result)
                throw new XmlBindingException("Unsupported CXF Result type: " + output.getClass().getName());
            throw new XmlBindingException("Unsupported CXF writer format: " + output.getClass().getName());
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new XmlBindingException("Cannot write CXF XML output", failure);
        }
    }

    @Override public void setAttachments(Collection<Attachment> attachments) { }
    @Override public void setProperty(String property, Object value) { }
    @Override public void setSchema(javax.xml.validation.Schema schema) { }
}
