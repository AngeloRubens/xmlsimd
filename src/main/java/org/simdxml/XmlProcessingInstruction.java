package org.simdxml;

import java.util.Objects;

public final class XmlProcessingInstruction implements XmlNode {
    private final String target, data;
    public XmlProcessingInstruction(String target, String data) { this.target = target; this.data = data; }
    public String target() { return target; }
    public String data() { return data; }
    @Override public Type type() { return Type.PROCESSING_INSTRUCTION; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof XmlProcessingInstruction)) return false; XmlProcessingInstruction that = (XmlProcessingInstruction) other; return Objects.equals(target, that.target) && Objects.equals(data, that.data); }
    @Override public int hashCode() { return Objects.hash(target, data); }
    @Override public String toString() { return "XmlProcessingInstruction[target=" + target + ", data=" + data + "]"; }
}
