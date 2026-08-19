package org.simdxml.javax;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.simdxml.SimdJaxbContext;

import java.util.Set;

final class SimdJakartaContext extends JAXBContext {
    private final SimdJaxbContext delegate;
    private final Set<Class<?>> types;
    private volatile JAXBContext coldPathDelegate;
    SimdJakartaContext(Class<?>[] types) {
        java.util.LinkedHashSet<Class<?>> copy = new java.util.LinkedHashSet<Class<?>>();
        java.util.Collections.addAll(copy, types.clone());
        this.types = java.util.Collections.unmodifiableSet(copy);
        delegate = SimdJaxbContext.newInstance(types);
    }
    @Override public Unmarshaller createUnmarshaller() {
        SimdJakartaUnmarshaller result = new SimdJakartaUnmarshaller(delegate, types);
        for (Class<?> type : delegate.adapterTypes()) result.installDefaultAdapter(type);
        return result;
    }
    @Override public Marshaller createMarshaller() {
        SimdJakartaMarshaller result = new SimdJakartaMarshaller(delegate.createMarshaller());
        for (Class<?> type : delegate.adapterTypes()) result.installDefaultAdapter(type);
        return result;
    }
    @SuppressWarnings("deprecation")
    @Override public javax.xml.bind.Validator createValidator() throws JAXBException {
        throw new JAXBException("Legacy Validator is not implemented; use Schema validation");
    }
    @Override public void generateSchema(javax.xml.bind.SchemaOutputResolver outputResolver) throws java.io.IOException {
        try {
            coldPathDelegate().generateSchema(outputResolver);
        } catch (JAXBException failure) {
            throw new java.io.IOException("JAXB schema generator is unavailable", failure);
        }
    }

    JAXBContext coldPathDelegate() throws JAXBException {
        JAXBContext current = coldPathDelegate;
        if (current != null) return current;
        synchronized (this) {
            current = coldPathDelegate;
            if (current == null) {
                current = ColdPathJaxbDelegate.create(types.toArray(new Class<?>[types.size()]));
                coldPathDelegate = current;
            }
        }
        return current;
    }
}
