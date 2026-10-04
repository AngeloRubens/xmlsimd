package org.simdxml;

import java.lang.reflect.Field;

final class ReflectionFieldAccess implements FieldAccess {
    private final Field field;
    ReflectionFieldAccess(Field field) { this.field = field; }
    @Override public void write(Object target, Object value) throws IllegalAccessException { field.set(target, value); }
    @Override public Object read(Object target) throws IllegalAccessException { return field.get(target); }
    @Override public Field directField() { return field; }
}
