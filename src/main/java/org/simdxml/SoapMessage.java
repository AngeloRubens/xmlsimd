package org.simdxml;

import java.util.Objects;
import java.util.Optional;

/** Structurally validated SOAP envelope. Payload semantics remain the responsibility of HL7/IHE schemas. */
public final class SoapMessage {
    private final SoapVersion version; private final XmlElement envelope, header, body;
    public SoapMessage(SoapVersion version, XmlElement envelope, XmlElement header, XmlElement body) { this.version = version; this.envelope = envelope; this.header = header; this.body = body; }
    public SoapVersion version() { return version; } public XmlElement envelope() { return envelope; }
    public XmlElement header() { return header; } public XmlElement body() { return body; }
    public Optional<XmlElement> optionalHeader() { return Optional.ofNullable(header); }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof SoapMessage)) return false; SoapMessage that = (SoapMessage) other; return version == that.version && Objects.equals(envelope, that.envelope) && Objects.equals(header, that.header) && Objects.equals(body, that.body); }
    @Override public int hashCode() { return Objects.hash(version, envelope, header, body); }
    @Override public String toString() { return "SoapMessage[version=" + version + ", envelope=" + envelope + ", header=" + header + ", body=" + body + "]"; }
}
