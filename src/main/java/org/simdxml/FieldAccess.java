package org.simdxml;

interface FieldAccess {
    void write(Object target, Object value) throws Throwable;
    Object read(Object target) throws Throwable;
    default java.lang.reflect.Field directField() { return null; }
}
