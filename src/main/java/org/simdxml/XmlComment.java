package org.simdxml;

import java.util.Objects;

public final class XmlComment implements XmlNode {
    private final String value;
    public XmlComment(String value) { this.value = value; }
    public String value() { return value; }
    @Override public Type type() { return Type.COMMENT; }
    @Override public boolean equals(Object other) { return this == other || other instanceof XmlComment && Objects.equals(value, ((XmlComment) other).value); }
    @Override public int hashCode() { return Objects.hashCode(value); }
    @Override public String toString() { return "XmlComment[value=" + value + "]"; }
}
