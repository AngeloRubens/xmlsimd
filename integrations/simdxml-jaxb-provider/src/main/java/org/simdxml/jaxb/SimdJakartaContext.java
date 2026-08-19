package org.simdxml.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
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
    @Override public void generateSchema(jakarta.xml.bind.SchemaOutputResolver outputResolver) throws java.io.IOException {
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
