package org.simdxml.weblogic;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.simdxml.jaxb.SimdJaxbContextFactory;

import java.util.Collections;

/**
 * WebLogic-friendly JAXBContext wrapper that resolves classes with the deployment context loader
 * while delegating all binding work to the normal simdxml Jakarta provider.
 */
public final class WeblogicSimdXmlJaxbContext extends JAXBContext {
    private final JAXBContext delegate;
    private final boolean mtomEnabled;

    public WeblogicSimdXmlJaxbContext(Class<?>[] classes, ClassLoader loader, boolean mtomEnabled)
            throws JAXBException {
        this.delegate = new SimdJaxbContextFactory().createContext(classes, Collections.<String, Object>emptyMap());
        this.mtomEnabled = mtomEnabled;
    }

    public WeblogicSimdXmlJaxbContext(String contextPath, ClassLoader loader, boolean mtomEnabled)
            throws JAXBException {
        ClassLoader effective = loader == null ? Thread.currentThread().getContextClassLoader() : loader;
        this.delegate = new SimdJaxbContextFactory().createContext(contextPath, effective,
                Collections.<String, Object>emptyMap());
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() { return mtomEnabled; }
    @Override public Marshaller createMarshaller() throws JAXBException { return delegate.createMarshaller(); }
    @Override public Unmarshaller createUnmarshaller() throws JAXBException { return delegate.createUnmarshaller(); }

    public static JAXBContext createContext(Class<?>... classes) throws JAXBException {
        return new WeblogicSimdXmlJaxbContext(classes, Thread.currentThread().getContextClassLoader(), false);
    }

    public static JAXBContext createContext(String contextPath) throws JAXBException {
        return new WeblogicSimdXmlJaxbContext(contextPath, Thread.currentThread().getContextClassLoader(), false);
    }

    public static JAXBContext createMtomEnabledContext(Class<?>... classes) throws JAXBException {
        return new WeblogicSimdXmlJaxbContext(classes, Thread.currentThread().getContextClassLoader(), true);
    }
}
