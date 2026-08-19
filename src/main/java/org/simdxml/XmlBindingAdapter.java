package org.simdxml;

/** JAXB-neutral adapter invoked from a precompiled binding property. */
public interface XmlBindingAdapter {
    Object marshal(Object value) throws Exception;
    Object unmarshal(Object value) throws Exception;
}
