package org.simdxml.javax;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.Map;

/**
 * Static factory contract used by JAXB 2.0-2.2 ContextFinder implementations
 * present in Java EE 8 application servers and older JDK 8 distributions.
 */
public final class SimdLegacyContextFactory {
    public static JAXBContext createContext(Class<?>[] classes, Map<String, ?> properties)
            throws JAXBException {
        if (classes == null || classes.length == 0)
            throw new JAXBException("At least one bound class is required");
        return new SimdJakartaContext(classes);
    }

    public static JAXBContext createContext(String contextPath, ClassLoader loader,
            Map<String, ?> properties) throws JAXBException {
        throw new JAXBException("Context-path discovery is not implemented; use JAXBContext.newInstance(Class<?>...)");
    }

    private SimdLegacyContextFactory() { }
}
