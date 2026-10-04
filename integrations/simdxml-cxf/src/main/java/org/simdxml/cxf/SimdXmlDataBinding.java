package org.simdxml.cxf;

import org.apache.cxf.databinding.DataBinding;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.service.Service;

import java.util.Collections;
import java.util.Map;

/**
 * simdxml DataBinding for Apache CXF.
 * Uses simdxml directly for XML parsing and binding to Java objects.
 */
public class SimdXmlDataBinding implements DataBinding {

    @Override
    public <T> DataReader<T> createReader(Class<T> type) {
        return new SimdXmlDataReader<>(type);
    }

    @Override
    public <T> DataWriter<T> createWriter(Class<T> type) {
        return new SimdXmlDataWriter<>(type);
    }

    @Override
    public Class<?>[] getSupportedReaderFormats() {
        // simdxml works with XMLStreamReader, Source, etc.
        // Returning common XML formats that CXF might use
        return new Class<?>[] {
            javax.xml.transform.Source.class,
            java.io.InputStream.class,
            java.io.Reader.class,
            javax.xml.stream.XMLStreamReader.class,
            javax.xml.stream.XMLEventReader.class
        };
    }

    @Override
    public Class<?>[] getSupportedWriterFormats() {
        return new Class<?>[] {
            java.io.OutputStream.class,
            java.io.Writer.class,
            javax.xml.transform.Result.class,
            javax.xml.stream.XMLStreamWriter.class,
            javax.xml.stream.XMLEventWriter.class
        };
    }

    @Override
    public void initialize(Service service) {
        // No initialization required for simdxml binding
    }

    @Override
    public Map<String, String> getDeclaredNamespaceMappings() {
        return Collections.emptyMap();
    }

    @Override
    public void setMtomEnabled(boolean enabled) {
        // MTOM handling would be done at the binding level, not in the data binding
    }

    @Override
    public boolean isMtomEnabled() {
        return false;
    }

    @Override
    public void setMtomThreshold(int threshold) {
        // Not implemented for this simple binding
    }

    @Override
    public int getMtomThreshold() {
        return 0;
    }
}
