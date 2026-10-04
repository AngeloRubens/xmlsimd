package org.simdxml.axis2;

import javax.xml.namespace.QName;

/**
 * Axis2 deployment facade. Axis2 1.8 has no public pluggable ADBDataBinding/BeanWriter SPI, so the
 * bridge exposes an explicit registry and codecs instead of implementing non-existent internals.
 */
public final class SimdXmlDataBinding {
    private final SimdXmlTypeMap typeMap = new SimdXmlTypeMap();

    public SimdXmlTypeMap getTypeMap() { return typeMap; }

    public <T> SimdXmlBeanWriter<T> getBeanWriter(Class<T> type) {
        return new SimdXmlBeanWriter<T>(type);
    }

    public void register(QName name, Class<?> type) { typeMap.addTypeMapping(type, name); }

    public byte[] serialize(Object value) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        SimdXmlBeanWriter writer = getBeanWriter(value.getClass());
        return writer.serialize(value);
    }

    public <T> T parse(byte[] xml, Class<T> type) { return getBeanWriter(type).populate(xml); }
}
