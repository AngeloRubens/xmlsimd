package org.simdxml.cxf;

import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.service.model.MessagePartInfo;
import org.simdxml.SimdJaxbContext;
import org.simdxml.XmlBindingException;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/** CXF reader backed exclusively by simdxml's public binding API. */
public final class SimdXmlDataReader<T> implements DataReader<T> {
    SimdXmlDataReader(Class<T> format) { }

    @Override public Object read(T input) {
        throw new XmlBindingException("CXF did not provide the expected Java type");
    }

    @Override public Object read(MessagePartInfo part, T input) {
        Class<?> type = part == null ? null : part.getTypeClass();
        if (type == null) throw new XmlBindingException("CXF message part has no Java type");
        return readTyped(input, type);
    }

    @Override public Object read(javax.xml.namespace.QName elementName, T input, Class<?> expectedType) {
        if (expectedType == null) throw new XmlBindingException("CXF did not provide the expected Java type");
        return readTyped(input, expectedType);
    }

    private static Object readTyped(Object input, Class<?> type) {
        if (input == null) return null;
        SimdJaxbContext context = SimdJaxbContext.newInstance(type);
        try {
            if (input instanceof XMLStreamReader)
                return context.createUnmarshaller().unmarshal((XMLStreamReader) input, type);
            if (input instanceof XMLEventReader)
                return context.createUnmarshaller().unmarshal((XMLEventReader) input, type);
            return context.unmarshal(bytes(input), type);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new XmlBindingException("Cannot read CXF XML input", failure);
        }
    }

    private static byte[] bytes(Object input) throws Exception {
        if (input instanceof InputStream) return ((InputStream) input).readAllBytes();
        if (input instanceof Reader) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[4096];
            for (int count; (count = ((Reader) input).read(buffer)) >= 0; ) text.append(buffer, 0, count);
            return text.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (input instanceof StreamSource) {
            StreamSource source = (StreamSource) input;
            if (source.getInputStream() != null) return source.getInputStream().readAllBytes();
            if (source.getReader() != null) return bytes(source.getReader());
        }
        if (input instanceof Source) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            TransformerFactory.newInstance().newTransformer().transform((Source) input, new StreamResult(output));
            return output.toByteArray();
        }
        throw new XmlBindingException("Unsupported CXF reader format: " + input.getClass().getName());
    }

    @Override public void setAttachments(Collection<Attachment> attachments) { }
    @Override public void setProperty(String property, Object value) { }
    @Override public void setSchema(javax.xml.validation.Schema schema) { }
}
