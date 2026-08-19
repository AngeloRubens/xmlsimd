package org.simdxml;

import java.util.Objects;

/** Character content. CDATA is retained as a distinct node type. */
public final class XmlText implements XmlNode {
    private final String value; private final boolean cdata;
    public XmlText(String value, boolean cdata) { this.value = value; this.cdata = cdata; }
    public String value() { return value; }
    public boolean cdata() { return cdata; }
    @Override public Type type() { return cdata ? Type.CDATA : Type.TEXT; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof XmlText)) return false; XmlText that = (XmlText) other; return cdata == that.cdata && Objects.equals(value, that.value); }
    @Override public int hashCode() { return 31 * Objects.hashCode(value) + (cdata ? 1231 : 1237); }
    @Override public String toString() { return "XmlText[value=" + value + ", cdata=" + cdata + "]"; }
}
