package org.simdxml.javax;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBContextFactory;
import javax.xml.bind.JAXBException;
import java.util.Map;

/** ServiceLoader entry point used by JAXBContext.newInstance. */
public final class SimdJaxbContextFactory implements JAXBContextFactory {
    @Override public JAXBContext createContext(Class<?>[] classes, Map<String, ?> properties) throws JAXBException {
        if (classes == null || classes.length == 0) throw new JAXBException("At least one bound class is required");
        return new SimdJakartaContext(classes);
    }
    @Override public JAXBContext createContext(String contextPath, ClassLoader loader, Map<String, ?> properties)
            throws JAXBException {
        throw new JAXBException("Context-path discovery is not implemented; use JAXBContext.newInstance(Class<?>...)");
    }
}
