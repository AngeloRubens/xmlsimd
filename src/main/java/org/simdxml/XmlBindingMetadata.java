package org.simdxml;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Precompiled, immutable JAXB annotation model. No reflection occurs in the event loop. */
final class XmlBindingMetadata {
    private static final String DEFAULT = "##default";
    private static final String JAKARTA = "jakarta.xml.bind.annotation.";
    private static final String JAVAX = "javax.xml.bind.annotation.";
    private static final String JAKARTA_ADAPTER = "jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter";
    private static final String JAVAX_ADAPTER = "javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter";
    private static final AccessMode ACCESS_MODE = configuredAccessMode();
    private static final ClassValue<List<Property>> PROPERTIES = new ClassValue<List<Property>>() {
        @Override protected List<Property> computeValue(Class<?> type) { return inspectProperties(type); }
    };
    private static final ClassValue<Map<XmlExpandedName, Property>> CHILDREN = new ClassValue<Map<XmlExpandedName, Property>>() {
        @Override protected Map<XmlExpandedName, Property> computeValue(Class<?> type) {
            Map<XmlExpandedName, Property> result = new HashMap<XmlExpandedName, Property>();
            for (Property property : properties(type))
                if (!property.attribute() && !property.value() && property.wrapperName() == null)
                    result.put(property.expandedName(), property);
            return java.util.Collections.unmodifiableMap(result);
        }
    };
    private static final ClassValue<Map<String, Property>> LOCAL_CHILDREN = new ClassValue<Map<String, Property>>() {
        @Override protected Map<String, Property> computeValue(Class<?> type) {
            Map<String, Property> result = new HashMap<String, Property>();
            for (Property property : properties(type))
                if (!property.attribute() && !property.value() && property.wrapperName() == null
                        && property.expandedName().namespace().isEmpty())
                    result.put(property.xmlName(), property);
            return java.util.Collections.unmodifiableMap(result);
        }
    };
    private static final ClassValue<Map<XmlExpandedName, Property>> WRAPPERS = new ClassValue<Map<XmlExpandedName, Property>>() {
        @Override protected Map<XmlExpandedName, Property> computeValue(Class<?> type){Map<XmlExpandedName,Property> result=new HashMap<XmlExpandedName,Property>();for(Property property:properties(type))if(property.wrapperName()!=null)result.put(property.wrapperName(),property);return java.util.Collections.unmodifiableMap(result);}
    };
    private static final ClassValue<List<Property>> ATTRIBUTES = new ClassValue<List<Property>>() {
        @Override protected List<Property> computeValue(Class<?> type) {
            List<Property> result = new ArrayList<Property>();
            for (Property property : properties(type)) if (property.attribute()) result.add(property);
            return java.util.Collections.unmodifiableList(result);
        }
    };
    private static final ClassValue<Property> VALUE = new ClassValue<Property>() {
        @Override protected Property computeValue(Class<?> type) {
            for (Property property : properties(type)) if (property.value()) return property;
            return null;
        }
    };
    private static final ClassValue<Constructor<?>> CONSTRUCTORS = new ClassValue<Constructor<?>>() {
        @Override protected Constructor<?> computeValue(Class<?> type) {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor;
            } catch (ReflectiveOperationException e) {
                throw new XmlBindingException("Cannot construct " + type.getName(), e);
            }
        }
    };
    private static final ClassValue<XmlExpandedName> ROOTS = new ClassValue<XmlExpandedName>() {
        @Override protected XmlExpandedName computeValue(Class<?> type) {
            String name = annotationName(type, JAKARTA + "XmlRootElement", JAVAX + "XmlRootElement");
            String namespace = annotationNamespace(type, JAKARTA + "XmlRootElement", JAVAX + "XmlRootElement");
            return new XmlExpandedName(namespace, name == null || DEFAULT.equals(name) ? type.getSimpleName() : name);
        }
    };
    private static final ClassValue<XmlExpandedName> TYPE_NAMES = new ClassValue<XmlExpandedName>() {
        @Override protected XmlExpandedName computeValue(Class<?> type) {
            String name=annotationName(type,JAKARTA+"XmlType",JAVAX+"XmlType");
            String namespace=annotationNamespace(type,JAKARTA+"XmlType",JAVAX+"XmlType");
            if(name==null||DEFAULT.equals(name))name=decapitalize(type.getSimpleName());
            return new XmlExpandedName(namespace,name);
        }
    };
    private static final ClassValue<Map<XmlExpandedName,Class<?>>> POLYMORPHIC_TYPES = new ClassValue<Map<XmlExpandedName,Class<?>>>() {
        @Override protected Map<XmlExpandedName,Class<?>> computeValue(Class<?> type){
            Map<XmlExpandedName,Class<?>> result=new HashMap<XmlExpandedName,Class<?>>();result.put(typeName(type),type);
            for(java.lang.annotation.Annotation annotation:type.getAnnotations()){
                String name=annotation.annotationType().getName();if(!name.equals(JAKARTA+"XmlSeeAlso")&&!name.equals(JAVAX+"XmlSeeAlso"))continue;
                try{Class<?>[] values=(Class<?>[])annotation.annotationType().getMethod("value").invoke(annotation);for(Class<?> value:values)if(type.isAssignableFrom(value))result.put(typeName(value),value);}
                catch(ReflectiveOperationException e){throw new XmlBindingException("Cannot read XmlSeeAlso on "+type.getName(),e);}
            }
            return java.util.Collections.unmodifiableMap(result);
        }
    };
    /* DatatypeFactory has no thread-safety guarantee. Fixed stripes avoid both a global lock and
       one heavyweight ThreadLocal value per virtual thread. */
    private static final javax.xml.datatype.DatatypeFactory[] DATATYPES = datatypeFactories(8);
    private static final ClassValue<Map<String, Object>> ENUM_VALUES = new ClassValue<Map<String, Object>>() {
        @Override protected Map<String, Object> computeValue(Class<?> type) {
            Map<String,Object> values=new HashMap<String,Object>();
            Object[] constants=type.getEnumConstants();
            for(Object constant:constants){String lexical=((Enum<?>)constant).name();try{Field field=type.getField(((Enum<?>)constant).name());String annotated=annotationString(field,JAKARTA+"XmlEnumValue",JAVAX+"XmlEnumValue","value");if(annotated!=null)lexical=annotated;}catch(NoSuchFieldException ignored){}values.put(lexical,constant);}
            return java.util.Collections.unmodifiableMap(values);
        }
    };

    static List<Property> properties(Class<?> type) { return PROPERTIES.get(type); }
    static Map<XmlExpandedName, Property> children(Class<?> type) { return CHILDREN.get(type); }
    static Map<String, Property> localChildren(Class<?> type) { return LOCAL_CHILDREN.get(type); }
    static Map<XmlExpandedName, Property> wrappers(Class<?> type) { return WRAPPERS.get(type); }
    static List<Property> attributes(Class<?> type) { return ATTRIBUTES.get(type); }
    static Property valueProperty(Class<?> type) { return VALUE.get(type); }
    static Constructor<?> constructor(Class<?> type) { return CONSTRUCTORS.get(type); }
    static String root(Class<?> type) { return ROOTS.get(type).localName(); }
    static XmlExpandedName rootName(Class<?> type) { return ROOTS.get(type); }
    static XmlExpandedName typeName(Class<?> type){return TYPE_NAMES.get(type);}
    static Map<XmlExpandedName,Class<?>> polymorphicTypes(Class<?> type){return POLYMORPHIC_TYPES.get(type);}
    static String accessStrategy() {
        return (ACCESS_MODE == AccessMode.VARHANDLE ? AccessMode.VARHANDLE : AccessMode.REFLECTION)
                .name().toLowerCase(java.util.Locale.ROOT);
    }

    static void prewarm(Class<?> type) { prewarm(type, new java.util.HashSet<>()); }
    private static void prewarm(Class<?> type, java.util.Set<Class<?>> visited) {
        if (!visited.add(type)) return;
        properties(type); children(type); attributes(type); valueProperty(type); root(type);typeName(type);polymorphicTypes(type);
        if (!scalar(type)) constructor(type);
        for (Property property : properties(type)) {
            Class<?> nested = property.adapterType() == null
                    ? (property.list() ? property.itemType() : property.rawType()) : property.xmlType();
            if (!scalar(nested)) prewarm(nested, visited);
        }
    }

    static void installDefaultAdapters(Class<?> type, Map<Class<?>, XmlBindingAdapter> target,
            java.util.Set<Class<?>> visited) {
        if (!visited.add(type) || scalar(type)) return;
        for (Property property : properties(type)) {
            if (property.adapterType() != null && !target.containsKey(property.adapterType()))
                target.put(property.adapterType(), newAdapter(property.adapterType()));
            Class<?> nested = property.adapterType() == null
                    ? (property.list() ? property.itemType() : property.rawType()) : property.xmlType();
            installDefaultAdapters(nested, target, visited);
        }
    }

    static void collectAdapterTypes(Class<?> type, java.util.Set<Class<?>> target,
            java.util.Set<Class<?>> visited) {
        if (!visited.add(type) || scalar(type)) return;
        for (Property property : properties(type)) {
            if (property.adapterType() != null) target.add(property.adapterType());
            Class<?> nested = property.adapterType() == null
                    ? (property.list() ? property.itemType() : property.rawType()) : property.xmlType();
            collectAdapterTypes(nested, target, visited);
        }
    }

    private static XmlBindingAdapter newAdapter(Class<?> adapterType) {
        try {
            final Object instance = adapterType.getDeclaredConstructor().newInstance();
            final Method marshal = adapterType.getMethod("marshal", Object.class);
            final Method unmarshal = adapterType.getMethod("unmarshal", Object.class);
            return new XmlBindingAdapter() {
                @Override public Object marshal(Object value) throws Exception {
                    try { return marshal.invoke(instance, value); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw adapterFailure(failure); }
                }
                @Override public Object unmarshal(Object value) throws Exception {
                    try { return unmarshal.invoke(instance, value); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw adapterFailure(failure); }
                }
            };
        } catch (ReflectiveOperationException failure) {
            throw new XmlBindingException("Cannot initialize XmlAdapter " + adapterType.getName(), failure);
        }
    }

    private static Exception adapterFailure(java.lang.reflect.InvocationTargetException failure) {
        Throwable cause = failure.getCause();
        return cause instanceof Exception ? (Exception) cause : new XmlBindingException("XmlAdapter failed", cause);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convert(String value, Class<?> type) {
        if (type == String.class) return value;
        String v = value.trim();
        if (type == int.class || type == Integer.class) return Integer.valueOf(v);
        if (type == long.class || type == Long.class) return Long.valueOf(v);
        if (type == double.class || type == Double.class) return xmlDouble(v);
        if (type == float.class || type == Float.class) return (float) xmlDouble(v);
        if (type == short.class || type == Short.class) return Short.valueOf(v);
        if (type == byte.class || type == Byte.class) return Byte.valueOf(v);
        if (type == boolean.class || type == Boolean.class) {
            if (v.equals("true") || v.equals("1")) return true;
            if (v.equals("false") || v.equals("0")) return false;
            throw new XmlBindingException("Invalid XML boolean: " + v);
        }
        if (type == java.math.BigInteger.class) return new java.math.BigInteger(v);
        if (type == java.math.BigDecimal.class) return new java.math.BigDecimal(v);
        if (type == char.class || type == Character.class) {
            if (v.length() != 1) throw new XmlBindingException("Expected one character, got: " + v);
            return v.charAt(0);
        }
        if (type == byte[].class) return java.util.Base64.getDecoder().decode(v);
        if (type == java.net.URI.class) return java.net.URI.create(v);
        if (type == java.net.URL.class) try { return new java.net.URL(v); } catch (java.net.MalformedURLException e) { throw new XmlBindingException("Invalid URL: "+v,e); }
        if (type == javax.xml.datatype.XMLGregorianCalendar.class) {
            javax.xml.datatype.DatatypeFactory factory=datatypeFactoryForThread();
            synchronized(factory){return factory.newXMLGregorianCalendar(v);}
        }
        if (type == javax.xml.datatype.Duration.class) {
            javax.xml.datatype.DatatypeFactory factory=datatypeFactoryForThread();
            synchronized(factory){return factory.newDuration(v);}
        }
        if (type == java.time.LocalDate.class) return java.time.LocalDate.parse(v);
        if (type == java.time.LocalTime.class) return java.time.LocalTime.parse(v);
        if (type == java.time.LocalDateTime.class) return java.time.LocalDateTime.parse(v);
        if (type == java.time.OffsetDateTime.class) return java.time.OffsetDateTime.parse(v);
        if (type == java.time.OffsetTime.class) return java.time.OffsetTime.parse(v);
        if (type == java.time.Instant.class) return java.time.Instant.parse(v);
        if (type == java.util.Date.class) return java.util.Date.from(java.time.Instant.parse(v));
        if (java.util.Calendar.class.isAssignableFrom(type)) {
            javax.xml.datatype.DatatypeFactory factory=datatypeFactoryForThread();
            synchronized(factory){return factory.newXMLGregorianCalendar(v).toGregorianCalendar();}
        }
        if (type == javax.xml.namespace.QName.class) return javax.xml.namespace.QName.valueOf(v);
        if (type.isEnum()) { Object result=ENUM_VALUES.get(type).get(v); if(result!=null)return result; throw new XmlBindingException("Invalid XML enum value: "+v); }
        throw new XmlBindingException("Unsupported scalar type: " + type.getName());
    }

    static String lexical(Object value) {
        if (value instanceof byte[]) return java.util.Base64.getEncoder().encodeToString((byte[])value);
        if (value instanceof javax.xml.datatype.XMLGregorianCalendar) return ((javax.xml.datatype.XMLGregorianCalendar)value).toXMLFormat();
        if (value instanceof javax.xml.datatype.Duration) return value.toString();
        if (value instanceof java.util.Date) return java.time.format.DateTimeFormatter.ISO_INSTANT.format(((java.util.Date)value).toInstant());
        if (value instanceof java.util.GregorianCalendar) {
            javax.xml.datatype.DatatypeFactory factory=datatypeFactoryForThread();
            synchronized(factory){return factory.newXMLGregorianCalendar((java.util.GregorianCalendar)value).toXMLFormat();}
        }
        if (value instanceof Enum<?>) {
            Class<?> type=value.getClass();
            for(Map.Entry<String,Object> entry:ENUM_VALUES.get(type).entrySet())if(entry.getValue()==value)return entry.getKey();
        }
        if (value instanceof Double) {
            Double number = (Double) value;
            if (number.isNaN()) return "NaN";
            if (number == Double.POSITIVE_INFINITY) return "INF";
            if (number == Double.NEGATIVE_INFINITY) return "-INF";
        } else if (value instanceof Float) {
            Float number = (Float) value;
            if (number.isNaN()) return "NaN";
            if (number == Float.POSITIVE_INFINITY) return "INF";
            if (number == Float.NEGATIVE_INFINITY) return "-INF";
        }
        return String.valueOf(value);
    }

    static boolean scalar(Class<?> type) {
        return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type)
                || type == Boolean.class || type == Character.class || type.isEnum() || type == byte[].class
                || type == java.net.URI.class || type == java.net.URL.class
                || javax.xml.datatype.XMLGregorianCalendar.class.isAssignableFrom(type)
                || javax.xml.datatype.Duration.class.isAssignableFrom(type)
                || java.util.Date.class.isAssignableFrom(type) || java.util.Calendar.class.isAssignableFrom(type)
                || type == javax.xml.namespace.QName.class || type.getName().startsWith("java.time.");
    }

    private static javax.xml.datatype.DatatypeFactory[] datatypeFactories(int count) {
        javax.xml.datatype.DatatypeFactory[] result=new javax.xml.datatype.DatatypeFactory[count];
        try { for(int i=0;i<count;i++)result[i]=javax.xml.datatype.DatatypeFactory.newInstance(); return result; }
        catch (javax.xml.datatype.DatatypeConfigurationException e) { throw new ExceptionInInitializerError(e); }
    }
    private static javax.xml.datatype.DatatypeFactory datatypeFactoryForThread() {
        long id=Thread.currentThread().getId();
        int mixed=(int)(id^(id>>>32));
        return DATATYPES[mixed&(DATATYPES.length-1)];
    }

    private static double xmlDouble(String value) {
        if ("INF".equals(value)) return Double.POSITIVE_INFINITY;
        if ("-INF".equals(value)) return Double.NEGATIVE_INFINITY;
        if ("NaN".equals(value)) return Double.NaN;
        return Double.parseDouble(value);
    }

    private static List<Property> inspectProperties(Class<?> type) {
        List<Property> result = new ArrayList<Property>();
        java.util.Set<String> included = new java.util.HashSet<String>();
        String access = accessorType(type);
        List<Class<?>> hierarchy = new ArrayList<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
            hierarchy.add(0, current);
        int index = 0;
        for (Class<?> declaring : hierarchy) {
            for (Field field : declaring.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers)
                        || hasAnnotation(field, JAKARTA + "XmlTransient", JAVAX + "XmlTransient")) continue;
                boolean annotated = bindingAnnotation(field);
                boolean implicit = "FIELD".equals(access) && !java.lang.reflect.Modifier.isTransient(modifiers)
                        || "PUBLIC_MEMBER".equals(access) && java.lang.reflect.Modifier.isPublic(modifiers);
                if (!annotated && !implicit) continue;
                field.setAccessible(true);
                result.add(property(index++, field.getName(), field.getType(), field.getGenericType(),
                        annotationName(field, JAKARTA + "XmlAttribute", JAVAX + "XmlAttribute"),
                        annotationName(field, JAKARTA + "XmlElement", JAVAX + "XmlElement"),
                        annotationNamespace(field, JAKARTA + "XmlAttribute", JAVAX + "XmlAttribute"),
                        annotationNamespace(field, JAKARTA + "XmlElement", JAVAX + "XmlElement"),
                        hasAnnotation(field, JAKARTA + "XmlValue", JAVAX + "XmlValue"), fieldAccess(field),
                        adapterType(field), "hexBinary".equals(annotationString(field,JAKARTA+"XmlSchemaType",JAVAX+"XmlSchemaType","name")),wrapperName(field,field.getName()),
                        annotationString(field,JAKARTA+"XmlElement",JAVAX+"XmlElement","defaultValue"),
                        annotationBoolean(field,JAKARTA+"XmlElement",JAVAX+"XmlElement","nillable")));
                included.add(field.getName());
            }
        }
        java.util.Map<String, Method[]> methods = beanMethods(hierarchy);
        for (Map.Entry<String, Method[]> entry : methods.entrySet()) {
            if (included.contains(entry.getKey())) continue;
            Method getter = entry.getValue()[0], setter = entry.getValue()[1];
            if (getter == null || setter == null) continue;
            AnnotatedElement annotations = bindingAnnotation(getter) ? getter : setter;
            boolean annotated = bindingAnnotation(getter) || bindingAnnotation(setter);
            boolean publicPair = java.lang.reflect.Modifier.isPublic(getter.getModifiers())
                    && java.lang.reflect.Modifier.isPublic(setter.getModifiers());
            if (!annotated && !(("PROPERTY".equals(access) || "PUBLIC_MEMBER".equals(access)) && publicPair)) continue;
            if (hasAnnotation(annotations, JAKARTA + "XmlTransient", JAVAX + "XmlTransient")) continue;
            result.add(property(index++, entry.getKey(), getter.getReturnType(), getter.getGenericReturnType(),
                    annotationName(annotations, JAKARTA + "XmlAttribute", JAVAX + "XmlAttribute"),
                    annotationName(annotations, JAKARTA + "XmlElement", JAVAX + "XmlElement"),
                    annotationNamespace(annotations, JAKARTA + "XmlAttribute", JAVAX + "XmlAttribute"),
                    annotationNamespace(annotations, JAKARTA + "XmlElement", JAVAX + "XmlElement"),
                    hasAnnotation(annotations, JAKARTA + "XmlValue", JAVAX + "XmlValue"),
                    new ReflectionMethodAccess(getter, setter), adapterType(annotations),
                    "hexBinary".equals(annotationString(annotations,JAKARTA+"XmlSchemaType",JAVAX+"XmlSchemaType","name")),wrapperName(annotations,entry.getKey()),
                    annotationString(annotations,JAKARTA+"XmlElement",JAVAX+"XmlElement","defaultValue"),
                    annotationBoolean(annotations,JAKARTA+"XmlElement",JAVAX+"XmlElement","nillable")));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private static Property property(int index, String javaName, Class<?> rawType, Type genericType,
            String attributeName, String elementName, String attributeNamespace, String elementNamespace,
            boolean value, FieldAccess access, Class<?> adapterType, boolean hexBinary, XmlExpandedName wrapperName,
            String defaultValue, boolean nillable) {
        String annotated = attributeName != null ? attributeName : elementName != null ? elementName : DEFAULT;
        String xmlName = DEFAULT.equals(annotated) ? javaName : annotated;
        String namespace = attributeName != null ? attributeNamespace : elementName != null ? elementNamespace : DEFAULT;
        if (namespace == null || DEFAULT.equals(namespace)) namespace = XmlExpandedName.EMPTY_NAMESPACE;
        boolean multiple = rawType.isArray() && rawType != byte[].class || java.util.Collection.class.isAssignableFrom(rawType);
        Class<?> itemType = rawType.isArray() && rawType != byte[].class
                ? rawType.getComponentType() : multiple ? listItem(genericType) : rawType;
        Class<?> xmlType = adapterType == null ? itemType : adapterValueType(adapterType);
        return new Property(index, new XmlExpandedName(namespace, xmlName), wrapperName, rawType, itemType, xmlType, adapterType,
                multiple, multiple && (rawType.isArray() || !rawType.isAssignableFrom(ArrayList.class)),
                attributeName != null, value, hexBinary, normalizedDefault(defaultValue), nillable, access);
    }

    private static String normalizedDefault(String value) {
        return value == null || "\u0000".equals(value) ? null : value;
    }

    private static XmlExpandedName wrapperName(AnnotatedElement element,String javaName){
        String name=annotationString(element,JAKARTA+"XmlElementWrapper",JAVAX+"XmlElementWrapper","name");if(name==null)return null;
        String namespace=annotationString(element,JAKARTA+"XmlElementWrapper",JAVAX+"XmlElementWrapper","namespace");
        if(DEFAULT.equals(name))name=javaName;if(namespace==null||DEFAULT.equals(namespace))namespace=XmlExpandedName.EMPTY_NAMESPACE;
        return new XmlExpandedName(namespace,name);
    }

    private static Class<?> adapterType(AnnotatedElement element) {
        for (java.lang.annotation.Annotation annotation : element.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (!name.equals(JAKARTA_ADAPTER) && !name.equals(JAVAX_ADAPTER)) continue;
            try { return (Class<?>) annotation.annotationType().getMethod("value").invoke(annotation); }
            catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot read XmlJavaTypeAdapter", e); }
        }
        return null;
    }

    private static Class<?> adapterValueType(Class<?> adapter) {
        Type parent = adapter.getGenericSuperclass();
        if (parent instanceof ParameterizedType) {
            Type value = ((ParameterizedType) parent).getActualTypeArguments()[0];
            if (value instanceof Class<?>) return (Class<?>) value;
        }
        throw new XmlBindingException("XmlAdapter must declare a concrete ValueType: " + adapter.getName());
    }

    private static Class<?> listItem(Type type) {
        if (type instanceof ParameterizedType) {
            Type item = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (item instanceof Class<?>) return (Class<?>) item;
        }
        throw new XmlBindingException("Collection properties must declare a concrete item type");
    }

    private static boolean bindingAnnotation(AnnotatedElement element) {
        return hasAnnotation(element, JAKARTA + "XmlAttribute", JAVAX + "XmlAttribute")
                || hasAnnotation(element, JAKARTA + "XmlElement", JAVAX + "XmlElement")
                || hasAnnotation(element, JAKARTA + "XmlValue", JAVAX + "XmlValue")
                || hasAnnotation(element, JAKARTA_ADAPTER, JAVAX_ADAPTER);
    }

    private static String accessorType(Class<?> type) {
        java.lang.annotation.Annotation annotation = null;
        for (java.lang.annotation.Annotation candidate : type.getAnnotations())
            if (candidate.annotationType().getName().equals("jakarta.xml.bind.annotation.XmlAccessorType")
                    || candidate.annotationType().getName().equals("javax.xml.bind.annotation.XmlAccessorType")) {
                annotation = candidate; break;
            }
        if (annotation == null) return "PUBLIC_MEMBER";
        try { return String.valueOf(annotation.annotationType().getMethod("value").invoke(annotation)); }
        catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot read XmlAccessorType", e); }
    }

    private static java.util.Map<String, Method[]> beanMethods(List<Class<?>> hierarchy) {
        java.util.Map<String, Method[]> result = new java.util.LinkedHashMap<String, Method[]>();
        for (Class<?> declaring : hierarchy) for (Method method : declaring.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
            String name = method.getName(), property = null; int slot = -1;
            if (method.getParameterTypes().length == 0 && method.getReturnType() != void.class
                    && name.startsWith("get") && name.length() > 3) { property = decapitalize(name.substring(3)); slot = 0; }
            else if (method.getParameterTypes().length == 0 && method.getReturnType() == boolean.class
                    && name.startsWith("is") && name.length() > 2) { property = decapitalize(name.substring(2)); slot = 0; }
            else if (method.getParameterTypes().length == 1 && name.startsWith("set") && name.length() > 3) {
                property = decapitalize(name.substring(3)); slot = 1;
            }
            if (property != null) {
                Method[] pair = result.get(property);
                if (pair == null) result.put(property, pair = new Method[2]);
                pair[slot] = method;
            }
        }
        return result;
    }

    private static String decapitalize(String value) {
        if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1)))
            return value;
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean hasAnnotation(AnnotatedElement element, String firstType, String secondType) {
        for (java.lang.annotation.Annotation annotation : element.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (name.equals(firstType) || name.equals(secondType)) return true;
        }
        return false;
    }

    private static String annotationName(AnnotatedElement element, String firstType, String secondType) {
        return annotationString(element, firstType, secondType, "name");
    }

    private static String annotationNamespace(AnnotatedElement element, String firstType, String secondType) {
        String value = annotationString(element, firstType, secondType, "namespace");
        return value == null || DEFAULT.equals(value) ? XmlExpandedName.EMPTY_NAMESPACE : value;
    }

    private static String annotationString(AnnotatedElement element,
            String firstType, String secondType, String method) {
        java.lang.annotation.Annotation annotation = null;
        for (java.lang.annotation.Annotation candidate : element.getAnnotations()) {
            String name = candidate.annotationType().getName();
            if (name.equals(firstType) || name.equals(secondType)) { annotation = candidate; break; }
        }
        if (annotation == null) return null;
        try { return (String) annotation.annotationType().getMethod(method).invoke(annotation); }
        catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot read " + annotation.annotationType(), e); }
    }

    private static boolean annotationBoolean(AnnotatedElement element,
            String firstType, String secondType, String method) {
        java.lang.annotation.Annotation annotation = null;
        for (java.lang.annotation.Annotation candidate : element.getAnnotations()) {
            String name = candidate.annotationType().getName();
            if (name.equals(firstType) || name.equals(secondType)) { annotation = candidate; break; }
        }
        if (annotation == null) return false;
        try { return ((Boolean) annotation.annotationType().getMethod(method).invoke(annotation)).booleanValue(); }
        catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot read " + annotation.annotationType(), e); }
    }

    private static FieldAccess fieldAccess(Field field) {
        // A/B testing on JRE 25 shows warmed Field.set faster for this object graph; auto preserves it.
        if (ACCESS_MODE != AccessMode.VARHANDLE) return new ReflectionFieldAccess(field);
        try {
            java.lang.reflect.Constructor<?> constructor = Class.forName("org.simdxml.VarHandleFieldAccess")
                    .getDeclaredConstructor(Field.class);
            constructor.setAccessible(true);
            return (FieldAccess) constructor.newInstance(field);
        } catch (ReflectiveOperationException unavailable) {
            throw new XmlBindingException("Cannot create VarHandle for " + field, unavailable);
        }
    }

    private static AccessMode configuredAccessMode() {
        String value = System.getProperty("org.simdxml.binding.access", "auto");
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        if ("auto".equals(normalized)) return AccessMode.AUTO;
        if ("varhandle".equals(normalized) || "var-handle".equals(normalized)) return AccessMode.VARHANDLE;
        if ("reflection".equals(normalized) || "reflect".equals(normalized)) return AccessMode.REFLECTION;
        throw new IllegalArgumentException("Unknown binding access strategy: " + value);
    }

    static final class Property {
        private final int index; private final XmlExpandedName xmlName, wrapperName;
        private final Class<?> rawType, itemType, xmlType, adapterType;
        private final boolean list, materialize, attribute, value, hexBinary, nillable;
        private final String defaultValue; private final FieldAccess fieldAccess;
        Property(int index, XmlExpandedName xmlName, XmlExpandedName wrapperName, Class<?> rawType, Class<?> itemType,
                Class<?> xmlType, Class<?> adapterType, boolean list,
                boolean materialize, boolean attribute, boolean value, boolean hexBinary,
                String defaultValue, boolean nillable, FieldAccess fieldAccess) {
            this.index = index; this.xmlName = xmlName; this.wrapperName=wrapperName; this.rawType = rawType; this.itemType = itemType;
            this.xmlType = xmlType; this.adapterType = adapterType;
            this.list = list; this.materialize = materialize; this.attribute = attribute; this.value = value; this.hexBinary=hexBinary;
            this.defaultValue=defaultValue; this.nillable=nillable;
            this.fieldAccess = fieldAccess;
        }
        int index() { return index; } String xmlName() { return xmlName.localName(); }
        XmlExpandedName expandedName() { return xmlName; } Class<?> rawType() { return rawType; }
        XmlExpandedName wrapperName(){return wrapperName;}
        Class<?> itemType() { return itemType; } boolean list() { return list; }
        Class<?> xmlType() { return xmlType; } Class<?> adapterType() { return adapterType; }
        boolean requiresMaterialization() { return materialize; }
        boolean attribute() { return attribute; } boolean value() { return value; }
        boolean hexBinary() { return hexBinary; }
        boolean nillable() { return nillable; }
        String defaultValue() { return defaultValue; }
        Object convert(String text) {
            if (!hexBinary) return XmlBindingMetadata.convert(text, xmlType);
            String value=text.trim(); if((value.length()&1)!=0)throw new XmlBindingException("Odd-length hexBinary");
            byte[] bytes=new byte[value.length()/2];for(int i=0;i<bytes.length;i++){int hi=Character.digit(value.charAt(i*2),16),lo=Character.digit(value.charAt(i*2+1),16);if(hi<0||lo<0)throw new XmlBindingException("Invalid hexBinary");bytes[i]=(byte)((hi<<4)|lo);}return bytes;
        }
        String lexical(Object value) {
            if (!hexBinary) return XmlBindingMetadata.lexical(value);
            byte[] bytes=(byte[])value; char[] out=new char[bytes.length*2]; final char[] hex="0123456789ABCDEF".toCharArray();for(int i=0;i<bytes.length;i++){int b=bytes[i]&255;out[i*2]=hex[b>>>4];out[i*2+1]=hex[b&15];}return new String(out);
        }
        void write(Object target, Object fieldValue) throws IllegalAccessException {
            try { fieldAccess.write(target, fieldValue); }
            catch (IllegalAccessException e) { throw e; }
            catch (Throwable e) { throw new XmlBindingException("Cannot write property " + xmlName, e); }
        }
        Object read(Object target) {
            try {
                return fieldAccess.read(target);
            } catch (Throwable e) {
                throw new XmlBindingException("Cannot read property " + xmlName, e);
            }
        }
    }

    private enum AccessMode { AUTO, VARHANDLE, REFLECTION }

    private XmlBindingMetadata() { }
}
