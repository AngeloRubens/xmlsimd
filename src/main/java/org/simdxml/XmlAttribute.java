package org.simdxml;

import java.util.Objects;

public final class XmlAttribute {
    private final String name, value;
    public XmlAttribute(String name, String value) { this.name = name; this.value = value; }
    public String name() { return name; }
    public String value() { return value; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof XmlAttribute)) return false; XmlAttribute that = (XmlAttribute) other; return Objects.equals(name, that.name) && Objects.equals(value, that.value); }
    @Override public int hashCode() { return Objects.hash(name, value); }
    @Override public String toString() { return "XmlAttribute[name=" + name + ", value=" + value + "]"; }
}
