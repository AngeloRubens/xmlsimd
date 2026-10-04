package org.simdxml;

import java.util.ArrayList;
import java.util.List;

/**
 * Push binder for the DirectSimdXmlParser; it keeps values in DirectXmlByteSlice until conversion.
 * Frames are pooled in an array stack and the binder itself is reused by the parser, so the only
 * per-document allocations left are the bound objects themselves.
 */
final class DirectXmlBeanBinder implements DirectXmlEventConsumerEx {
    private static final ClassValue<byte[]> ROOT_NAMES = new ClassValue<byte[]>() {
        @Override protected byte[] computeValue(Class<?> type) {
            return XmlBindingMetadata.root(type).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        }
    };
    private static final byte[] XMLNS = "xmlns".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private Class<?> rootType;
    private byte[] rootName;
    private Frame[] frames = new Frame[16];
    private int depth;
    private Object result;
    /* Binder-owned view over text bounds retained from an event whose flyweight has moved on. */
    private final DirectXmlByteSlice retained = new DirectXmlByteSlice();

    DirectXmlBeanBinder(Class<?> rootType) { reset(rootType); }

    /** Rebinds the pooled frames to a new document; the parser keeps one binder per thread. */
    void reset(Class<?> rootType) {
        this.rootType = rootType;
        this.rootName = ROOT_NAMES.get(rootType);
        for (int i = 0; i < depth; i++) frames[i].clear();
        depth = 0;
        result = null;
    }

    @Override public void onEvent(XmlEvent event, DirectXmlByteSlice name, DirectXmlByteSlice text, DirectXmlAttributes attrs) {
        if (event == XmlEvent.START_ELEMENT) start(name, attrs);
        else if (event == XmlEvent.TEXT) append(text, false);
        else if (event == XmlEvent.CDATA) append(text, true);
        else if (event == XmlEvent.END_ELEMENT) end();
    }

    Object result() {
        if (result == null) throw new XmlBindingException("Document has no bound root");
        return result;
    }

    private Frame peek() { return depth == 0 ? null : frames[depth - 1]; }

    private void start(DirectXmlByteSlice name, DirectXmlAttributes attrs) {
        if (name.qualified()) throw new XmlBindingException("Direct binding requires namespace-free XML");
        Frame parent = peek();
        XmlBindingMetadata.Property parentProperty = null;
        Class<?> type = rootType;
        if (parent == null) {
            if (!name.localEqualsAscii(rootName))
                throw new XmlBindingException("Unexpected root element for " + rootType.getName());
        } else {
            if (parent.skip != 0) { parent.skip++; return; }
            parentProperty = findChild(parent.type, name);
            if (parentProperty == null) { parent.skip++; return; }
            type = parentProperty.xmlType();
        }
        Frame frame = push(type, parent, parentProperty);
        if (frame.plan == null) return;
        for (int i = 0, size = attrs.size(); i < size; i++) {
            DirectXmlByteSlice attrName = attrs.name(i);
            if (attrName.qualified() || attrName.localEqualsAscii(XMLNS))
                throw new XmlBindingException("Direct binding requires namespace-free XML");
            XmlBindingMetadata.Property property = findAttribute(frame.plan, attrName);
            if (property != null) {
                frame.write(property, property.convert(attrs.rawValue(i)));
            }
        }
    }

    private Frame push(Class<?> type, Frame parent, XmlBindingMetadata.Property parentProperty) {
        if (depth == frames.length) frames = java.util.Arrays.copyOf(frames, depth * 2);
        Frame frame = frames[depth];
        if (frame == null) frames[depth] = frame = new Frame();
        depth++;
        frame.init(type, parent, parentProperty);
        return frame;
    }

    private void append(DirectXmlByteSlice text, boolean cdata) {
        Frame frame = peek();
        if (frame == null || frame.skip != 0) return;
        if (text.length() == 0) return;
        // Event slices are parser-owned flyweights reset at the next event, but the segment behind
        // them lives for the whole document. Keep the bounds of the first segment and decode
        // nothing: a scalar that never splits — the common case — converts straight from bytes at
        // END, so an int or a boolean never allocates a String.
        if (frame.rawSegment == null && frame.text == null && frame.builder == null) {
            frame.rawSegment = text.rawSegment();
            frame.rawFrom = text.rawOffset();
            frame.rawTo = text.rawOffset() + text.length();
            frame.rawCdata = cdata;
            // The byte-level conversions expand entities, so they are only safe for ordinary text
            // that carries none. CDATA is excluded outright: an '&' inside it is literal, and no
            // conversion that might expand it may see the segment.
            frame.rawLiteral = !cdata && !text.contains((byte) '&');
            return;
        }
        if (frame.rawSegment != null) {
            frame.text = decode(retained.reset(frame.rawSegment, frame.rawFrom, frame.rawTo), frame.rawCdata);
            frame.rawSegment = null;
        }
        String part = decode(text, cdata);
        if (part.isEmpty()) return;
        if (frame.builder != null) frame.builder.append(part);
        else if (frame.text == null) frame.text = part;
        else {
            frame.builder = new StringBuilder(frame.text.length() + part.length());
            frame.builder.append(frame.text).append(part);
            frame.text = null;
        }
    }

    /** CDATA content is literal; ordinary text carries the entities the scanner already validated. */
    private static String decode(DirectXmlByteSlice slice, boolean cdata) {
        return cdata ? slice.decodeUtf8() : slice.decodeXmlText();
    }

    /** The retained single segment, or null when the text was split, absent or not byte-convertible. */
    private DirectXmlByteSlice raw(Frame frame) {
        if (frame.rawSegment == null) return null;
        // Carry the scanner's answer across: rawLiteral already means "ordinary text, no entity".
        if (frame.rawLiteral)
            return retained.reset(frame.rawSegment, frame.rawFrom, frame.rawTo, frame.rawFrom, false);
        return retained.reset(frame.rawSegment, frame.rawFrom, frame.rawTo);
    }

    private void end() {
        Frame frame = peek();
        if (frame == null) return;
        if (frame.skip != 0) { frame.skip--; return; }
        depth--;
        Frame parent = frame.parent;
        Object value;
        if (frame.scalar) {
            value = convertScalar(frame, frame.parentProperty, frame.type);
        } else {
            if (frame.value != null)
                frame.write(frame.value, convertScalar(frame, frame.value, frame.value.xmlType()));
            value = frame.instance;
        }
        XmlBindingMetadata.Property parentProperty = frame.parentProperty;
        frame.clear();
        if (parent == null) { result = value; return; }
        if (parentProperty.list()) {
            @SuppressWarnings("unchecked") List<Object> list = (List<Object>) parent.read(parentProperty);
            if (list == null) { list = new ArrayList<Object>(); parent.write(parentProperty, list); }
            list.add(value);
        } else parent.write(parentProperty, value);
    }

    /**
     * Converts the element's text. When a single segment was retained and a precompiled property
     * is available, {@code Property.convert(XmlRawValue)} reads the bytes directly — the same
     * conversion the standard binder's raw fast path uses.
     */
    private Object convertScalar(Frame frame, XmlBindingMetadata.Property property, Class<?> type) {
        if (type == javax.xml.namespace.QName.class)
            return new javax.xml.namespace.QName("", lexical(frame));
        if (property == null) return XmlBindingMetadata.convert(lexical(frame), type);
        // Byte conversion only for a single literal segment; a reference has to be expanded first.
        if (frame.rawSegment != null && frame.rawLiteral) return property.convert((XmlRawValue) raw(frame));
        return property.convert(lexical(frame));
    }

    private String lexical(Frame frame) {
        DirectXmlByteSlice raw = raw(frame);
        if (raw != null) return decode(raw, frame.rawCdata);
        return frame.lexical();
    }

    /** Byte dispatch when the plan allows it, name lookup otherwise — never silently no attributes. */
    private static XmlBindingMetadata.Property findAttribute(BindingPlan plan, DirectXmlByteSlice name) {
        if (plan.attributeHashDispatch()) return plan.findAttribute(name);
        return plan.localAttributes.get(name.decodeUtf8());
    }

    private static XmlBindingMetadata.Property findChild(Class<?> type, DirectXmlByteSlice name) {
        BindingPlan plan = XmlBindingMetadata.plan(type);
        if (plan.hashDispatch()) return plan.findLocal(name);
        return plan.localChildren.get(name.decodeUtf8());
    }

    /** Pooled stack slot. Fields are reset by {@link #init} and released by {@link #clear}. */
    private static final class Frame {
        Class<?> type; Frame parent; XmlBindingMetadata.Property parentProperty;
        BindingPlan plan; GeneratedBeanAccess generated; Object instance; boolean scalar;
        XmlBindingMetadata.Property value; String text; StringBuilder builder; int skip;
        java.lang.foreign.MemorySegment rawSegment; long rawFrom, rawTo; boolean rawCdata, rawLiteral;

        void init(Class<?> type, Frame parent, XmlBindingMetadata.Property parentProperty) {
            this.type = type; this.parent = parent; this.parentProperty = parentProperty;
            this.text = null; this.builder = null; this.skip = 0; this.rawSegment = null;
            this.scalar = XmlBindingMetadata.scalar(type);
            if (scalar) { this.plan = null; this.value = null; this.generated = null; this.instance = null; return; }
            this.plan = XmlBindingMetadata.plan(type);
            this.value = plan.value;
            this.generated = plan.generated;
            try { this.instance = generated == null ? XmlBindingMetadata.constructor(type).newInstance() : generated.newInstance(); }
            catch (ReflectiveOperationException e) { throw new XmlBindingException("Cannot construct " + type.getName(), e); }
        }
        /** Drops references so a pooled frame never keeps a bound graph alive between documents. */
        void clear() { type = null; parent = null; parentProperty = null; plan = null; generated = null; instance = null; value = null; text = null; builder = null; skip = 0; rawSegment = null; }
        String lexical() {
            if (builder == null) return text == null ? "" : text;
            return builder.append(text == null ? "" : text).toString();
        }
        Object read(XmlBindingMetadata.Property property) { return generated == null ? property.read(instance) : generated.read(instance, property.index()); }
        void write(XmlBindingMetadata.Property property, Object value) {
            if (generated != null) { generated.write(instance, property.index(), value); return; }
            try { property.write(instance, value); }
            catch (IllegalAccessException e) { throw new XmlBindingException("Cannot write property " + property.xmlName(), e); }
        }
    }
}
