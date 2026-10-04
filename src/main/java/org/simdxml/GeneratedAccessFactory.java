package org.simdxml;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Creates and caches tiny ASM adapters which replace reflective field access in the hot path. */
final class GeneratedAccessFactory {
    /** Marks a type that cannot be generated, so the attempt is never repeated per instance. */
    private static final GeneratedBeanAccess UNAVAILABLE = new GeneratedBeanAccess() {
        @Override public Object newInstance() { throw new UnsupportedOperationException(); }
        @Override public void write(Object target, int propertyIndex, Object value) { throw new UnsupportedOperationException(); }
        @Override public Object read(Object target, int propertyIndex) { throw new UnsupportedOperationException(); }
    };
    /* ClassValue, not a static map: an application-server redeploy must be able to unload the bean. */
    private static final ClassValue<GeneratedBeanAccess> CACHE = new ClassValue<GeneratedBeanAccess>() {
        @Override protected GeneratedBeanAccess computeValue(Class<?> type) {
            GeneratedBeanAccess created = create(type);
            return created == null ? UNAVAILABLE : created;
        }
    };
    private static final AtomicLong SEQUENCE = new AtomicLong();

    static GeneratedBeanAccess get(Class<?> type) {
        GeneratedBeanAccess cached = CACHE.get(type);
        return cached == UNAVAILABLE ? null : cached;
    }

    /** Escape hatch for A/B measurement against reflection and VarHandle access. */
    private static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("org.simdxml.binding.generated", "true"));

    private static GeneratedBeanAccess create(Class<?> type) {
        if (!ENABLED) return null;
        if (!Modifier.isPublic(type.getModifiers()) || !visibleFromHere(type)) return null;
        try {
            Constructor<?> ctor = type.getDeclaredConstructor();
            if (!Modifier.isPublic(ctor.getModifiers())) return null;
            for (XmlBindingMetadata.Property property : XmlBindingMetadata.properties(type)) {
                java.lang.reflect.Field field = property.directField();
                if (field == null || !Modifier.isPublic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) return null;
            }
            String internal = Type.getInternalName(type);
            String generated = "org/simdxml/GeneratedAccess$" + Long.toHexString(SEQUENCE.incrementAndGet());
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cw.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_PUBLIC, generated, null,
                    "java/lang/Object", new String[]{Type.getInternalName(GeneratedBeanAccess.class)});
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode(); mv.visitVarInsn(Opcodes.ALOAD, 0); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false); mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 0); mv.visitEnd();
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "newInstance", "()Ljava/lang/Object;", null, null);
            mv.visitCode(); mv.visitTypeInsn(Opcodes.NEW, internal); mv.visitInsn(Opcodes.DUP); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, internal, "<init>", "()V", false); mv.visitInsn(Opcodes.ARETURN); mv.visitMaxs(0, 0); mv.visitEnd();
            emitWrite(cw, type, internal);
            emitRead(cw, type, internal);
            cw.visitEnd();
            // Lookup#defineClass exists since Java 9. Reflection keeps the portable Java 8
            // compatibility build linkable; Java 8 simply falls back to normal field access.
            Class<?> generatedType = (Class<?>) MethodHandles.Lookup.class
                    .getMethod("defineClass", byte[].class).invoke(MethodHandles.lookup(), cw.toByteArray());
            return (GeneratedBeanAccess) generatedType.getDeclaredConstructor().newInstance();
        } catch (Throwable unavailable) {
            return null;
        }
    }

    /**
     * The adapter is defined in simdxml's own loader, so it can only link against a bean this
     * loader can see. In an application server the bean often lives in a child loader; generating
     * there would fail with NoClassDefFoundError on first use instead of falling back to reflection.
     */
    private static boolean visibleFromHere(Class<?> type) {
        ClassLoader here = GeneratedAccessFactory.class.getClassLoader();
        if (type.getClassLoader() == here) return true;
        try { return Class.forName(type.getName(), false, here) == type; }
        catch (ClassNotFoundException | LinkageError unavailable) { return false; }
    }

    private static void emitWrite(ClassWriter cw, Class<?> type, String internal) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "write", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        XmlBindingMetadata.Property[] properties = XmlBindingMetadata.properties(type).toArray(new XmlBindingMetadata.Property[0]);
        for (int i = 0; i < properties.length; i++) {
            mv.visitVarInsn(Opcodes.ILOAD, 2); mv.visitLdcInsn(i); org.objectweb.asm.Label next = new org.objectweb.asm.Label(); mv.visitJumpInsn(Opcodes.IF_ICMPNE, next);
            java.lang.reflect.Field field = properties[i].directField();
            mv.visitVarInsn(Opcodes.ALOAD, 1); mv.visitTypeInsn(Opcodes.CHECKCAST, internal); mv.visitVarInsn(Opcodes.ALOAD, 3); unbox(mv, field.getType());
            mv.visitFieldInsn(Opcodes.PUTFIELD, internal, field.getName(), Type.getDescriptor(field.getType())); mv.visitInsn(Opcodes.RETURN); mv.visitLabel(next);
        }
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException"); mv.visitInsn(Opcodes.DUP); mv.visitLdcInsn("Unknown property index"); mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false); mv.visitInsn(Opcodes.ATHROW); mv.visitMaxs(0, 0); mv.visitEnd();
    }

    private static void emitRead(ClassWriter cw, Class<?> type, String internal) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "read", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null); mv.visitCode();
        XmlBindingMetadata.Property[] properties = XmlBindingMetadata.properties(type).toArray(new XmlBindingMetadata.Property[0]);
        for (int i = 0; i < properties.length; i++) {
            mv.visitVarInsn(Opcodes.ILOAD, 2); mv.visitLdcInsn(i); org.objectweb.asm.Label next = new org.objectweb.asm.Label(); mv.visitJumpInsn(Opcodes.IF_ICMPNE, next);
            java.lang.reflect.Field field = properties[i].directField(); mv.visitVarInsn(Opcodes.ALOAD, 1); mv.visitTypeInsn(Opcodes.CHECKCAST, internal); mv.visitFieldInsn(Opcodes.GETFIELD, internal, field.getName(), Type.getDescriptor(field.getType())); box(mv, field.getType()); mv.visitInsn(Opcodes.ARETURN); mv.visitLabel(next);
        }
        mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN); mv.visitMaxs(0, 0); mv.visitEnd();
    }

    private static void unbox(MethodVisitor mv, Class<?> type) {
        if (!type.isPrimitive()) { mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(type)); return; }
        String wrapper = Type.getInternalName(wrapper(type)); mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
        String method = type == boolean.class ? "booleanValue" : type == char.class ? "charValue" : type == byte.class ? "byteValue" : type == short.class ? "shortValue" : type == int.class ? "intValue" : type == long.class ? "longValue" : type == float.class ? "floatValue" : "doubleValue";
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, method, "()" + Type.getDescriptor(type), false);
    }
    private static void box(MethodVisitor mv, Class<?> type) { if (type.isPrimitive()) mv.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(wrapper(type)), "valueOf", "(" + Type.getDescriptor(type) + ")" + Type.getDescriptor(wrapper(type)), false); }
    private static Class<?> wrapper(Class<?> type) { if (type == boolean.class) return Boolean.class; if (type == byte.class) return Byte.class; if (type == short.class) return Short.class; if (type == char.class) return Character.class; if (type == int.class) return Integer.class; if (type == long.class) return Long.class; if (type == float.class) return Float.class; return Double.class; }
    private GeneratedAccessFactory() { }
}
