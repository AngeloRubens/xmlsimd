package org.simdxml;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/** Reusable direct-memory SOAP/WS-A/HL7 projection; only returned fields are materialized. */
final class DirectHealthcareSoapInspector implements DirectXmlEventConsumerEx {
    private static final byte[] ENVELOPE = ascii("Envelope"), HEADER = ascii("Header"), BODY = ascii("Body"),
            ACTION = ascii("Action"), MESSAGE_ID = ascii("MessageID");
    private final DirectSimdXmlParser parser;
    private final String[] namespacePrefixes = new String[8];
    private final String[] namespaceValues = new String[8];
    private int namespaceCount;
    private SoapVersion version;
    private String envelopePrefix, action, messageId, payload, payloadNamespace, capture;
    private int depth, bodyDepth;
    private boolean headerSeen, bodySeen;

    DirectHealthcareSoapInspector(DirectSimdXmlParser parser) { this.parser = parser; }
    HealthcareMessageInfo inspect(MemorySegment input) {
        reset(); parser.scan(input, this);
        if (version == null || !bodySeen) throw new SoapValidationException("SOAP Body is mandatory");
        return new HealthcareMessageInfo(version, protocol(payload, payloadNamespace), action, messageId, payload);
    }
    @Override public void onEvent(XmlEvent event, DirectXmlByteSlice name, DirectXmlByteSlice text, DirectXmlAttributes attrs) {
        if (event == XmlEvent.START_ELEMENT) {
            depth++;
            if (depth == 1) {
                if (!name.localEqualsAscii(ENVELOPE)) throw new SoapValidationException("Root element must be SOAP Envelope");
                String qname = name.decodeUtf8(); envelopePrefix = prefix(qname);
                for (int i = 0; i < attrs.size(); i++) {
                    String attrName = attrs.name(i).decodeUtf8();
                    if (attrName.equals("xmlns") || attrName.startsWith("xmlns:"))
                        putNamespace(attrName.equals("xmlns") ? "" : attrName.substring(6), attrs.rawValue(i).decodeUtf8());
                }
                String namespace = namespace(envelopePrefix);
                for (SoapVersion candidate : SoapVersion.values()) if (candidate.namespace().equals(namespace)) version = candidate;
                if (version == null) throw new SoapValidationException("Unsupported or missing SOAP namespace");
            } else if (depth == 2 && name.localEqualsAscii(HEADER) && !headerSeen && !bodySeen) headerSeen = true;
            else if (depth == 2 && name.localEqualsAscii(BODY) && !bodySeen) { bodySeen = true; bodyDepth = depth; }
            else if (depth == 2) throw new SoapValidationException("SOAP Envelope permits only Header followed by Body");
            else if (bodyDepth > 0 && depth == bodyDepth + 1 && payload == null) {
                payload = name.decodeUtf8(); payloadNamespace = namespace(prefix(payload));
            }
            if (headerSeen && !bodySeen && name.localEqualsAscii(ACTION)) capture = "Action";
            else if (headerSeen && !bodySeen && name.localEqualsAscii(MESSAGE_ID)) capture = "MessageID";
        } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && capture != null) {
            String value = text.decodeUtf8(); if (capture.equals("Action")) action = value; else messageId = value;
        } else if (event == XmlEvent.END_ELEMENT) {
            if (capture != null && (capture.equals("Action") ? name.localEqualsAscii(ACTION) : name.localEqualsAscii(MESSAGE_ID))) capture = null;
            depth--;
        }
    }
    private void reset() {
        version = null; envelopePrefix = action = messageId = payload = payloadNamespace = capture = null;
        depth = 0; bodyDepth = -1; headerSeen = bodySeen = false; namespaceCount = 0;
    }
    private void putNamespace(String prefix, String value) {
        if (namespaceCount == namespacePrefixes.length) return;
        namespacePrefixes[namespaceCount] = prefix; namespaceValues[namespaceCount++] = value;
    }
    private String namespace(String prefix) {
        for (int i = 0; i < namespaceCount; i++) if (namespacePrefixes[i].equals(prefix)) return namespaceValues[i];
        return null;
    }
    private static HealthcareProtocol protocol(String payload, String namespace) {
        if (payload == null) return HealthcareProtocol.SOAP;
        String local = payload.substring(payload.indexOf(':') + 1);
        if ("http://hl7.org/fhir".equals(namespace)) return HealthcareProtocol.FHIR_XML;
        if ("urn:hl7-org:v3".equals(namespace)) return local.equals("ClinicalDocument") ? HealthcareProtocol.CDA : HealthcareProtocol.HL7_V3;
        return HealthcareProtocol.SOAP;
    }
    private static String prefix(String qname) { int colon = qname.indexOf(':'); return colon < 0 ? "" : qname.substring(0, colon); }
    private static byte[] ascii(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
}
