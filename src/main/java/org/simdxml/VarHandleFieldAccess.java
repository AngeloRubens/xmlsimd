package org.simdxml;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** JDK 9+ provider, loaded only when explicitly selected. */
final class VarHandleFieldAccess implements FieldAccess {
    private final VarHandle handle;
    VarHandleFieldAccess(Field field) throws IllegalAccessException {
        handle = MethodHandles.privateLookupIn(field.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(field);
    }
    @Override public void write(Object target, Object value) { handle.set(target, value); }
    @Override public Object read(Object target) { return handle.get(target); }
}
