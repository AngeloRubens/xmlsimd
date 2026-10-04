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
    private static final ClassValue<BindingPlan> PLANS = new ClassValue<BindingPlan>() {
        @Override protected BindingPlan computeValue(Class<?> type) { return new BindingPlan(type); }
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
    static BindingPlan plan(Class<?> type) { return PLANS.get(type); }
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
                || type == javax.xml.namespace.QName.class || type.getName().startsWith("java.time.")
                || type.getName().equals("jakarta.activation.DataHandler") || type.getName().equals("javax.activation.DataHandler");
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

    private static boolean asciiSpace(byte value) {
        return value == ' ' || value == '\t' || value == '\n' || value == '\r';
    }

    static float parseFloat(XmlRawValue raw) {
        int n = (int) raw.length();
        int start = 0, end = n;
        while (start < end && asciiSpace(raw.byteAt(start))) start++;
        while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
        if (start >= end) return 0.0f;

        boolean negative = false;
        int integerPart = 0;
        int fractionalPart = 0;
        int fractionalDigits = 0;
        int exponent = 0;
        boolean hasExponent = false;
        boolean exponentNegative = false;
        int state = 0; // 0=integer, 1=fraction, 2=exponent
        int i = start;

        // Handle sign
        if (raw.byteAt(i) == '-') { negative = true; i++; }
        else if (raw.byteAt(i) == '+') { i++; }

        // Parse integer part
        while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
            integerPart = integerPart * 10 + (raw.byteAt(i) - '0');
            i++;
        }

        // Parse fractional part
        if (i < end && raw.byteAt(i) == '.') {
            i++; // skip '.'
            state = 1;
            while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
                if (fractionalDigits < 9) { // limit to prevent overflow
                    fractionalPart = fractionalPart * 10 + (raw.byteAt(i) - '0');
                    fractionalDigits++;
                }
                i++;
            }
        }

        // Parse exponent
        if (i < end && (raw.byteAt(i) == 'e' || raw.byteAt(i) == 'E')) {
            i++; // skip 'e' or 'E'
            state = 2;
            hasExponent = true;
            if (i < end && raw.byteAt(i) == '-') { exponentNegative = true; i++; }
            else if (i < end && raw.byteAt(i) == '+') { i++; }

            while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
                exponent = exponent * 10 + (raw.byteAt(i) - '0');
                i++;
            }
        }

        // Apply exponent to fractional part
        if (hasExponent) {
            if (exponentNegative) exponent = -exponent;
            fractionalDigits -= exponent;
        }

        // Normalize fractional part
        float value = integerPart;
        if (fractionalDigits > 0) {
            float fraction = fractionalPart;
            for (int j = 0; j < fractionalDigits; j++) {
                fraction /= 10.0f;
            }
            value += fraction;
        } else if (fractionalDigits < 0) {
            // Handle case where exponent shifted decimal point left of integer part
            for (int j = 0; j < -fractionalDigits; j++) {
                value /= 10.0f;
            }
        }

        // Apply exponent
        if (hasExponent) {
            for (int j = 0; j < Math.abs(exponent); j++) {
                if (exponent > 0) value *= 10.0f;
                else value /= 10.0f;
            }
        }

        return negative ? -value : value;
    }

    static double parseDouble(XmlRawValue raw) {
        int n = (int) raw.length();
        int start = 0, end = n;
        while (start < end && asciiSpace(raw.byteAt(start))) start++;
        while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
        if (start >= end) return 0.0;

        boolean negative = false;
        long integerPart = 0;
        long fractionalPart = 0;
        int fractionalDigits = 0;
        int exponent = 0;
        boolean hasExponent = false;
        boolean exponentNegative = false;
        int state = 0; // 0=integer, 1=fraction, 2=exponent
        int i = start;

        // Handle sign
        if (raw.byteAt(i) == '-') { negative = true; i++; }
        else if (raw.byteAt(i) == '+') { i++; }

        // Parse integer part
        while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
            // Check for overflow
            if (integerPart > (Long.MAX_VALUE - (raw.byteAt(i) - '0')) / 10) {
                // Fall back to Double.parseDouble for overflow cases
                String trimmed = raw.decodeUtf8(start, end - start).trim();
                if ("INF".equals(trimmed)) return Double.POSITIVE_INFINITY;
                if ("-INF".equals(trimmed)) return Double.NEGATIVE_INFINITY;
                if ("NaN".equals(trimmed)) return Double.NaN;
                return Double.parseDouble(trimmed);
            }
            integerPart = integerPart * 10 + (raw.byteAt(i) - '0');
            i++;
        }

        // Parse fractional part
        if (i < end && raw.byteAt(i) == '.') {
            i++; // skip '.'
            state = 1;
            while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
                if (fractionalDigits < 18) { // limit to prevent overflow
                    fractionalPart = fractionalPart * 10 + (raw.byteAt(i) - '0');
                    fractionalDigits++;
                }
                i++;
            }
        }

        // Parse exponent
        if (i < end && (raw.byteAt(i) == 'e' || raw.byteAt(i) == 'E')) {
            i++; // skip 'e' or 'E'
            state = 2;
            hasExponent = true;
            if (i < end && raw.byteAt(i) == '-') { exponentNegative = true; i++; }
            else if (i < end && raw.byteAt(i) == '+') { i++; }

            while (i < end && raw.byteAt(i) >= '0' && raw.byteAt(i) <= '9') {
                // Check for overflow
                if (exponent > Integer.MAX_VALUE / 10 - (raw.byteAt(i) - '0')) {
                    // Fall back to Double.parseDouble for overflow cases
                    String trimmed = raw.decodeUtf8(start, end - start).trim();
                    if ("INF".equals(trimmed)) return Double.POSITIVE_INFINITY;
                    if ("-INF".equals(trimmed)) return Double.NEGATIVE_INFINITY;
                    if ("NaN".equals(trimmed)) return Double.NaN;
                    return Double.parseDouble(trimmed);
                }
                exponent = exponent * 10 + (raw.byteAt(i) - '0');
                i++;
            }
        }

        // Apply exponent to fractional part
        if (hasExponent) {
            if (exponentNegative) exponent = -exponent;
            fractionalDigits -= exponent;
        }

        // Normalize fractional part
        double value = integerPart;
        if (fractionalDigits > 0) {
            double fraction = fractionalPart;
            for (int j = 0; j < fractionalDigits; j++) {
                fraction /= 10.0;
            }
            value += fraction;
        } else if (fractionalDigits < 0) {
            // Handle case where exponent shifted decimal point left of integer part
            for (int j = 0; j < -fractionalDigits; j++) {
                value /= 10.0;
            }
        }

        // Apply exponent
        if (hasExponent) {
            for (int j = 0; j < Math.abs(exponent); j++) {
                if (exponent > 0) value *= 10.0;
                else value /= 10.0;
            }
        }

        return negative ? -value : value;
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
                        annotationBoolean(field,JAKARTA+"XmlElement",JAVAX+"XmlElement","nillable"), field, declaring));
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
                    annotationBoolean(annotations,JAKARTA+"XmlElement",JAVAX+"XmlElement","nillable"),
                    annotations, getter.getDeclaringClass()));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private static Property property(int index, String javaName, Class<?> rawType, Type genericType,
            String attributeName, String elementName, String attributeNamespace, String elementNamespace,
            boolean value, FieldAccess access, Class<?> adapterType, boolean hexBinary, XmlExpandedName wrapperName,
            String defaultValue, boolean nillable, AnnotatedElement annotations, Class<?> declaring) {
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
                attributeName != null, value, hexBinary, normalizedDefault(defaultValue), nillable, access,
                annotationString(annotations, JAKARTA + "XmlMimeType", JAVAX + "XmlMimeType", "value"),
                hasAnnotation(annotations, JAKARTA + "XmlInlineBinaryData", JAVAX + "XmlInlineBinaryData")
                        || declaring != null && hasAnnotation(declaring, JAKARTA + "XmlInlineBinaryData", JAVAX + "XmlInlineBinaryData"),
                hasAnnotation(annotations, JAKARTA + "XmlAttachmentRef", JAVAX + "XmlAttachmentRef"));
    }

    /**
     * MTOM-capable value types, recognised by name so that the core never links against
     * {@code jakarta.activation} or {@code javax.activation}.
     */
    private static boolean binaryType(Class<?> type) {
        if (type == byte[].class) return true;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.equals("jakarta.activation.DataHandler") || name.equals("javax.activation.DataHandler")) return true;
        }
        return false;
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
        private final int xmlNameHash;
        private final byte[] xmlNameBytes;
        private final Class<?> rawType, itemType, xmlType, adapterType;
        private final boolean list, materialize, attribute, value, hexBinary, nillable;
        private final boolean binary, inlineBinary, attachmentRef;
        private final String mimeType;
        private final String defaultValue; private final FieldAccess fieldAccess;
        Property(int index, XmlExpandedName xmlName, XmlExpandedName wrapperName, Class<?> rawType, Class<?> itemType,
                Class<?> xmlType, Class<?> adapterType, boolean list,
                boolean materialize, boolean attribute, boolean value, boolean hexBinary,
                String defaultValue, boolean nillable, FieldAccess fieldAccess,
                String mimeType, boolean inlineBinary, boolean attachmentRef) {
            this.index = index; this.xmlName = xmlName; this.wrapperName=wrapperName; this.rawType = rawType; this.itemType = itemType;
            this.xmlNameHash = fnv1a(xmlName.localName());
            this.xmlNameBytes = asciiBytes(xmlName.localName());
            this.xmlType = xmlType; this.adapterType = adapterType;
            this.list = list; this.materialize = materialize; this.attribute = attribute; this.value = value; this.hexBinary=hexBinary;
            this.defaultValue=defaultValue; this.nillable=nillable;
            this.fieldAccess = fieldAccess;
            // Resolved once per class, never in the marshal/unmarshal loop.
            this.mimeType = mimeType; this.inlineBinary = inlineBinary; this.attachmentRef = attachmentRef;
            this.binary = !hexBinary && binaryType(xmlType);
        }
        int index() { return index; } String xmlName() { return xmlName.localName(); }
        byte[] xmlNameBytes() { return xmlNameBytes; }
        int xmlNameHash() { return xmlNameHash; }
        int xmlNameLength() { return xmlName.localName().length(); }
        XmlExpandedName expandedName() { return xmlName; } Class<?> rawType() { return rawType; }
        XmlExpandedName wrapperName(){return wrapperName;}
        Class<?> itemType() { return itemType; } boolean list() { return list; }
        Class<?> xmlType() { return xmlType; } Class<?> adapterType() { return adapterType; }
        boolean requiresMaterialization() { return materialize; }
        boolean attribute() { return attribute; } boolean value() { return value; }
        boolean hexBinary() { return hexBinary; }
        boolean nillable() { return nillable; }
        /** True when the value can travel as an MTOM/XOP or swaRef attachment: {@code byte[]} or a {@code DataHandler}. */
        boolean binary() { return binary; }
        /** {@code @XmlMimeType} value, or null. */
        String mimeType() { return mimeType; }
        /** {@code @XmlInlineBinaryData}: never optimize this property into an attachment. */
        boolean inlineBinary() { return inlineBinary; }
        /** {@code @XmlAttachmentRef}: bind through swaRef rather than XOP. */
        boolean attachmentRef() { return attachmentRef; }
        String defaultValue() { return defaultValue; }
        java.lang.reflect.Field directField() { return fieldAccess.directField(); }
        Object convert(String text) {
            if (!hexBinary) return XmlBindingMetadata.convert(text, xmlType);
            String value=text.trim(); if((value.length()&1)!=0)throw new XmlBindingException("Odd-length hexBinary");
            byte[] bytes=new byte[value.length()/2];for(int i=0;i<bytes.length;i++){int hi=Character.digit(value.charAt(i*2),16),lo=Character.digit(value.charAt(i*2+1),16);if(hi<0||lo<0)throw new XmlBindingException("Invalid hexBinary");bytes[i]=(byte)((hi<<4)|lo);}return bytes;
        }
        Object convert(XmlByteSlice raw) {
            if (hexBinary || xmlType == String.class || xmlType == Character.class || xmlType == char.class)
                return convert(raw.decodeUtf8());
            int n = raw.length();
            if (xmlType == int.class || xmlType == Integer.class) return Integer.valueOf(decimalInt(raw, n));
            if (xmlType == long.class || xmlType == Long.class) return Long.valueOf(decimalLong(raw, n));
            if (xmlType == short.class || xmlType == Short.class) return Short.valueOf((short) decimalInt(raw, n));
            if (xmlType == byte.class || xmlType == Byte.class) return Byte.valueOf((byte) decimalInt(raw, n));
            if (xmlType == boolean.class || xmlType == Boolean.class) return Boolean.valueOf(xmlBoolean(raw));
            if (xmlType == double.class || xmlType == Double.class) {
                double parsed = decimalDouble(raw, n);
                if (parsed == parsed) return Double.valueOf(parsed);
            } else if (xmlType == float.class || xmlType == Float.class) {
                double parsed = decimalDouble(raw, n);
                if (parsed == parsed) return Float.valueOf((float) parsed);
            }
            return convert(raw.decodeUtf8());
        }
        Object convert(XmlRawValue raw) {
            if (hexBinary || xmlType == String.class || xmlType == Character.class || xmlType == char.class)
                return convert(raw.decodeXmlText());
            int n = (int) raw.length();
            if (xmlType == int.class || xmlType == Integer.class) return Integer.valueOf(decimalInt(raw, n));
            if (xmlType == long.class || xmlType == Long.class) return Long.valueOf(decimalLong(raw, n));
            if (xmlType == short.class || xmlType == Short.class) return Short.valueOf((short) decimalInt(raw, n));
            if (xmlType == byte.class || xmlType == Byte.class) return Byte.valueOf((byte) decimalInt(raw, n));
            if (xmlType == boolean.class || xmlType == Boolean.class) return Boolean.valueOf(xmlBoolean(raw, n));
            if (xmlType == double.class || xmlType == Double.class) {
                double parsed = decimalDouble(raw, n);
                if (parsed == parsed) return Double.valueOf(parsed);
            } else if (xmlType == float.class || xmlType == Float.class) {
                double parsed = decimalDouble(raw, n);
                if (parsed == parsed) return Float.valueOf((float) parsed);
            }
            return convert(raw.decodeXmlText());
        }

        /**
         * Byte-level {@code xs:boolean}. The earlier fast path answered {@code false} for every
         * lexical form that was not {@code true} or {@code 1}, so {@code <b>yes</b>} bound silently
         * instead of being rejected the way the String path rejects it.
         */
        private static boolean xmlBoolean(XmlByteSlice raw) {
            if (raw.equalsAscii(TRUE) || raw.equalsAscii(ONE)) return true;
            if (raw.equalsAscii(FALSE) || raw.equalsAscii(ZERO)) return false;
            throw new XmlBindingException("Invalid XML boolean: " + raw.decodeUtf8());
        }

        /** Byte-level {@code xs:boolean}; the String form allocated one object per element. */
        private static boolean xmlBoolean(XmlRawValue raw, int n) {
            int start = 0, end = n;
            while (start < end && asciiSpace(raw.byteAt(start))) start++;
            while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
            if (matchesAscii(raw, start, end, TRUE) || matchesAscii(raw, start, end, ONE)) return true;
            if (matchesAscii(raw, start, end, FALSE) || matchesAscii(raw, start, end, ZERO)) return false;
            throw new XmlBindingException("Invalid XML boolean: " + raw.decodeUtf8().trim());
        }

        private static boolean matchesAscii(XmlRawValue raw, int start, int end, byte[] token) {
            if (end - start != token.length) return false;
            for (int i = 0; i < token.length; i++) if (raw.byteAt(start + i) != token[i]) return false;
            return true;
        }
        private static final byte[] TRUE = new byte[]{'t','r','u','e'}, ONE = new byte[]{'1'};
        private static final byte[] FALSE = new byte[]{'f','a','l','s','e'}, ZERO = new byte[]{'0'};
        /** 10^0 … 10^22 are the powers exactly representable as a double. */
        private static final double[] POW10 = {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
            1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22};
        private static final int MAX_POW10 = 22;
        /** Significands up to 2^53 are exact in a double. */
        private static final long MAX_EXACT_MANTISSA = 1L << 53;
        /** Long.MAX_VALUE has 19 digits, so 18 digits always fit without an overflow check per digit. */
        private static final int SAFE_DIGITS = 18;
        /**
         * Up to {@link #SAFE_DIGITS} decimal digits cannot overflow a {@code long}, so the digit loop
         * is a plain multiply-add and the range is checked once at the end. Longer input — which in
         * practice only means leading zeros — falls back to the JDK parser.
         */
        private static int decimalInt(XmlByteSlice raw, int n) {
            if (n == 0) return 0; int i = 0; boolean negative = raw.byteAt(0) == '-'; if (negative) i++;
            if (n - i > SAFE_DIGITS) return Integer.parseInt(raw.decodeUtf8().trim());
            long value = 0;
            for (; i < n; i++) {
                byte b = raw.byteAt(i);
                if (b < '0' || b > '9') throw new NumberFormatException(raw.decodeUtf8());
                value = value * 10 + (b - '0');
            }
            return toInt(negative ? -value : value, raw);
        }
        private static long decimalLong(XmlByteSlice raw, int n) {
            if (n == 0) return 0L; int i = 0; boolean negative = raw.byteAt(0) == '-'; if (negative) i++;
            if (n - i > SAFE_DIGITS) return Long.parseLong(raw.decodeUtf8().trim());
            long value = 0;
            for (; i < n; i++) {
                byte b = raw.byteAt(i);
                if (b < '0' || b > '9') throw new NumberFormatException(raw.decodeUtf8());
                value = value * 10 + (b - '0');
            }
            return negative ? -value : value;
        }
        /**
         * Decimal floating point straight from the bytes, with no intermediate String.
         *
         * <p>Only the form that can be proved correct is taken: at most 18 significant digits (so
         * the significand accumulates in a long without overflow), a significand no larger than
         * {@code 2^53} and a decimal exponent within ±22, which are exactly the conditions under
         * which one multiplication or division by an exact power of ten is the correctly rounded
         * result. Exponent notation, {@code INF}, {@code NaN} and anything longer fall back to the
         * JDK parser, signalled by returning {@code NaN} — a value this grammar can never produce.
         *
         * <p>Float uses the same result cast down, which is what the String path already does.
         */
        private static double decimalDouble(XmlRawValue raw, int n) {
            int start = 0, end = n;
            while (start < end && asciiSpace(raw.byteAt(start))) start++;
            while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
            if (start == end) return Double.NaN;
            int i = start;
            boolean negative = false;
            byte sign = raw.byteAt(i);
            if (sign == '-' || sign == '+') { negative = sign == '-'; i++; }
            long mantissa = 0;
            int digits = 0, exponent = 0;
            boolean anyDigit = false, seenDot = false;
            for (; i < end; i++) {
                byte b = raw.byteAt(i);
                if (b >= '0' && b <= '9') {
                    if (++digits > SAFE_DIGITS) return Double.NaN;
                    mantissa = mantissa * 10 + (b - '0');
                    if (seenDot) exponent--;
                    anyDigit = true;
                    continue;
                }
                if (b == '.' && !seenDot) { seenDot = true; continue; }
                return Double.NaN;
            }
            if (!anyDigit || mantissa > MAX_EXACT_MANTISSA || exponent < -MAX_POW10) return Double.NaN;
            double value = exponent < 0 ? mantissa / POW10[-exponent] : mantissa;
            return negative ? -value : value;
        }

        private static double decimalDouble(XmlByteSlice raw, int n) {
            int start = 0, end = n;
            while (start < end && asciiSpace(raw.byteAt(start))) start++;
            while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
            if (start == end) return Double.NaN;
            int i = start;
            boolean negative = false;
            byte sign = raw.byteAt(i);
            if (sign == '-' || sign == '+') { negative = sign == '-'; i++; }
            long mantissa = 0;
            int digits = 0, exponent = 0;
            boolean anyDigit = false, seenDot = false;
            for (; i < end; i++) {
                byte b = raw.byteAt(i);
                if (b >= '0' && b <= '9') {
                    if (++digits > SAFE_DIGITS) return Double.NaN;
                    mantissa = mantissa * 10 + (b - '0');
                    if (seenDot) exponent--;
                    anyDigit = true;
                    continue;
                }
                if (b == '.' && !seenDot) { seenDot = true; continue; }
                return Double.NaN;
            }
            if (!anyDigit || mantissa > MAX_EXACT_MANTISSA || exponent < -MAX_POW10) return Double.NaN;
            double value = exponent < 0 ? mantissa / POW10[-exponent] : mantissa;
            return negative ? -value : value;
        }

        private static int toInt(long value, XmlByteSlice raw) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new NumberFormatException(raw.decodeUtf8());
            return (int) value;
        }
        private static int toInt(long value, XmlRawValue raw) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new NumberFormatException(raw.decodeUtf8());
            return (int) value;
        }
        private static int decimalInt(XmlRawValue raw, int n) {
            int start = 0, end = n;
            while (start < end && asciiSpace(raw.byteAt(start))) start++;
            while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
            if (start == end) return 0;
            int i = start; boolean negative = raw.byteAt(i) == '-'; if (negative) i++;
            if (end - i > SAFE_DIGITS) return Integer.parseInt(raw.decodeUtf8().trim());
            long value = 0;
            for (; i < end; i++) {
                byte b = raw.byteAt(i);
                if (b < '0' || b > '9') throw new NumberFormatException(raw.decodeUtf8());
                value = value * 10 + (b - '0');
            }
            return toInt(negative ? -value : value, raw);
        }
        private static long decimalLong(XmlRawValue raw, int n) {
            int start = 0, end = n;
            while (start < end && asciiSpace(raw.byteAt(start))) start++;
            while (end > start && asciiSpace(raw.byteAt(end - 1))) end--;
            if (start == end) return 0L;
            int i = start; boolean negative = raw.byteAt(i) == '-'; if (negative) i++;
            if (end - i > SAFE_DIGITS) return Long.parseLong(raw.decodeUtf8().trim());
            long value = 0L;
            for (; i < end; i++) { byte b = raw.byteAt(i); if (b < '0' || b > '9') throw new NumberFormatException(raw.decodeUtf8()); value = value * 10 + (b - '0'); }
            return negative ? -value : value;
        }
        private static boolean asciiSpace(byte value) { return value == ' ' || value == '\t' || value == '\n' || value == '\r'; }
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
        private static int fnv1a(String value) { int hash=0x811c9dc5; for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c>0x7f)return -1;hash=(hash ^ c)*0x01000193;} return hash; }
        private static byte[] asciiBytes(String value) { for (int i=0;i<value.length();i++) if (value.charAt(i)>0x7f) return null; return value.getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    }

    private enum AccessMode { AUTO, VARHANDLE, REFLECTION }

    private XmlBindingMetadata() { }
}
