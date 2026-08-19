package org.simdxml.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/** Resolves the container JAXB implementation once, outside simdxml's hot path. */
final class ColdPathJaxbDelegate {
    static final String FACTORY_PROPERTY = "org.simdxml.jaxb.coldPathFactory";

    private static final String[] DEFAULT_FACTORIES = {
            "org.glassfish.jaxb.runtime.v2.ContextFactory",
            "org.eclipse.persistence.jaxb.JAXBContextFactory"
    };

    private ColdPathJaxbDelegate() { }

    static JAXBContext create(Class<?>[] types) throws JAXBException {
        String configured = System.getProperty(FACTORY_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return invoke(configured.trim(), types);
            } catch (ReflectiveOperationException failure) {
                throw new JAXBException("Configured cold-path JAXB factory is unavailable: " + configured, failure);
            }
        }
        Throwable lastFailure = null;
        for (int i = 0; i < DEFAULT_FACTORIES.length; i++) {
            try {
                return invoke(DEFAULT_FACTORIES[i], types);
            } catch (ClassNotFoundException failure) {
                lastFailure = failure;
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }
        throw new JAXBException("No container or standalone JAXB implementation is available for cold-path operations", lastFailure);
    }

    private static JAXBContext invoke(String factoryName, Class<?>[] types)
            throws ReflectiveOperationException, JAXBException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> factory = Class.forName(factoryName, true,
                loader == null ? ColdPathJaxbDelegate.class.getClassLoader() : loader);
        Method method = factory.getMethod("createContext", Class[].class, Map.class);
        try {
            return (JAXBContext) method.invoke(null, types, Collections.emptyMap());
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof JAXBException) throw (JAXBException) cause;
            throw new JAXBException("Cold-path JAXB factory failed: " + factoryName, cause);
        }
    }
}
