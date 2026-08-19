package org.simdxml;

import java.lang.reflect.Method;

/** Pre-resolved bean getter/setter pair; lookup never occurs in the binding loop. */
final class ReflectionMethodAccess implements FieldAccess {
    private final Method getter;
    private final Method setter;
    ReflectionMethodAccess(Method getter, Method setter) {
        this.getter = getter; this.setter = setter;
        getter.setAccessible(true); setter.setAccessible(true);
    }
    @Override public void write(Object target, Object value) throws Throwable { setter.invoke(target, value); }
    @Override public Object read(Object target) throws Throwable { return getter.invoke(target); }
}
