package org.simdxml;

import java.util.Map;

/** SOAP/WS-Addressing/HL7 projection. The fast implementation avoids constructing a document tree. */
final class HealthcareSoapInspector {
    private static final String HL7_V3 = "urn:hl7-org:v3";
    private static final String FHIR = "http://hl7.org/fhir";

    static HealthcareMessageInfo inspect(SimdXmlParser parser, byte[] input, boolean vertical) {
        SimdXmlStreamReader reader = parser.reusableStream(input);
        SoapVersion version = null;
        String envelopePrefix = null, action = null, messageId = null, payload = null, payloadNamespace = null;
        Map<String, String> envelopeAttributes = java.util.Collections.emptyMap();
        String capture = null; StringBuilder capturedText = new StringBuilder(96);
        int depth = 0, bodyDepth = -1; boolean headerSeen = false, bodySeen = false;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                depth++; String qname = reader.name(); String local = vertical ? null : local(qname);
                boolean envelope = vertical ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ENVELOPE, VerticalXmlTokens.ENVELOPE) : local.equals("Envelope");
                boolean header = vertical ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_HEADER, VerticalXmlTokens.HEADER) : local.equals("Header");
                boolean body = vertical ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_BODY, VerticalXmlTokens.BODY) : local.equals("Body");
                if (depth == 1) {
                    if (!envelope) throw new SoapValidationException("Root element must be SOAP Envelope");
                    envelopePrefix = prefix(qname); envelopeAttributes = reader.attributes();
                    version = soapVersion(namespace(envelopeAttributes, envelopePrefix));
                } else if (depth == 2 && header && samePrefix(qname, envelopePrefix) && !headerSeen && !bodySeen) headerSeen = true;
                else if (depth == 2 && body && samePrefix(qname, envelopePrefix) && !bodySeen) { bodySeen = true; bodyDepth = depth; }
                else if (depth == 2) throw new SoapValidationException("SOAP Envelope permits only Header followed by Body");
                else if (bodyDepth > 0 && depth == bodyDepth + 1 && payload == null) {
                    payload = qname; payloadNamespace = namespaceOrNull(reader.attributes(), prefix(qname));
                    if (payloadNamespace == null) payloadNamespace = namespaceOrNull(envelopeAttributes, prefix(qname));
                }
                boolean isAction = vertical ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ACTION, VerticalXmlTokens.ACTION) : local.equals("Action");
                boolean isMessageId = vertical ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_MESSAGE_ID, VerticalXmlTokens.MESSAGE_ID) : local.equals("MessageID");
                if ((!vertical || headerSeen && !bodySeen) && (isAction || isMessageId)) {
                    capture = isAction ? "Action" : "MessageID"; capturedText.setLength(0);
                }
            } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && capture != null) capturedText.append(reader.text());
            else if (event == XmlEvent.END_ELEMENT) {
                boolean capturedEnd = capture != null && (vertical
                        ? capture.equals("Action") ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ACTION, VerticalXmlTokens.ACTION)
                        : VerticalXmlTokens.is(reader, VerticalXmlTokens.H_MESSAGE_ID, VerticalXmlTokens.MESSAGE_ID)
                        : capture.equals(local(reader.name())));
                if (capturedEnd) {
                    if (capture.equals("Action")) action = capturedText.toString(); else messageId = capturedText.toString();
                    capture = null;
                }
                depth--;
            }
        }
        if (version == null || !bodySeen) throw new SoapValidationException("SOAP Body is mandatory");
        return new HealthcareMessageInfo(version, protocol(payload, payloadNamespace), action, messageId, payload);
    }

    static void fill(SimdXmlParser parser, byte[] input, boolean vertical, HealthcareFlyweight out) {
        out.clear();
        SimdXmlStreamReader reader = parser.reusableStream(input);
        SoapVersion version = null;
        String envelopePrefix = null, payload = null, payloadNamespace = null;
        Map<String, String> envelopeAttributes = java.util.Collections.emptyMap();
        String capture = null; boolean rawCapture = false;
        StringBuilder capturedText = out.splitText;
        int depth = 0, bodyDepth = -1;
        boolean headerSeen = false, bodySeen = false;
        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                depth++;
                String qname = reader.name();
                String local = vertical ? null : local(qname);
                boolean envelope = vertical
                        ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ENVELOPE, VerticalXmlTokens.ENVELOPE)
                        : local.equals("Envelope");
                boolean header = vertical
                        ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_HEADER, VerticalXmlTokens.HEADER)
                        : local.equals("Header");
                boolean body = vertical
                        ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_BODY, VerticalXmlTokens.BODY)
                        : local.equals("Body");
                if (depth == 1) {
                    if (!envelope) throw new SoapValidationException("Root element must be SOAP Envelope");
                    envelopePrefix = prefix(qname);
                    envelopeAttributes = reader.attributes();
                    version = soapVersion(namespace(envelopeAttributes, envelopePrefix));
                } else if (depth == 2 && header && samePrefix(qname, envelopePrefix) && !headerSeen && !bodySeen) {
                    headerSeen = true;
                } else if (depth == 2 && body && samePrefix(qname, envelopePrefix) && !bodySeen) {
                    bodySeen = true; bodyDepth = depth;
                } else if (depth == 2) {
                    throw new SoapValidationException("SOAP Envelope permits only Header followed by Body");
                } else if (bodyDepth > 0 && depth == bodyDepth + 1 && payload == null) {
                    payload = qname;
                    payloadNamespace = namespaceOrNull(reader.attributes(), prefix(qname));
                    if (payloadNamespace == null) payloadNamespace = namespaceOrNull(envelopeAttributes, prefix(qname));
                }
                /* Vertical mode restricts WS-A probes to Header; generic mode performs no protocol shortcut. */
                boolean isAction = vertical
                        ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ACTION, VerticalXmlTokens.ACTION)
                        : local.equals("Action");
                boolean isMessageId = vertical
                        ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_MESSAGE_ID, VerticalXmlTokens.MESSAGE_ID)
                        : local.equals("MessageID");
                if ((!vertical || headerSeen && !bodySeen) && (isAction || isMessageId)) {
                    capture = isAction ? "Action" : "MessageID"; capturedText.setLength(0); rawCapture = false;
                }
            } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && capture != null) {
                XmlByteSlice raw = reader.rawTextBytes();
                boolean plain = event == XmlEvent.CDATA || !raw.contains((byte) '&');
                if (!rawCapture && capturedText.length() == 0 && plain) {
                    out.capturedRaw.reset(raw); rawCapture = true;
                } else {
                    if (rawCapture) { capturedText.append(out.capturedRaw.decodeUtf8()); rawCapture = false; }
                    capturedText.append(reader.text());
                }
            } else if (event == XmlEvent.END_ELEMENT) {
                boolean capturedEnd = capture != null && (vertical
                        ? capture.equals("Action")
                            ? VerticalXmlTokens.is(reader, VerticalXmlTokens.H_ACTION, VerticalXmlTokens.ACTION)
                            : VerticalXmlTokens.is(reader, VerticalXmlTokens.H_MESSAGE_ID, VerticalXmlTokens.MESSAGE_ID)
                        : capture.equals(local(reader.name())));
                if (capturedEnd) {
                    XmlValueFlyweight value = capture.equals("Action") ? out.mutableAction() : out.mutableMessageId();
                    if (rawCapture) value.wrapPlainTrimmed(out.capturedRaw);
                    else value.wrapMaterialized(capturedText.toString().trim());
                    capture = null;
                }
                depth--;
            }
        }
        if (version == null || !bodySeen) throw new SoapValidationException("SOAP Body is mandatory");
        out.metadata(version, protocol(payload, payloadNamespace), payload);
    }

    private static HealthcareProtocol protocol(String payload, String namespace) {
        if (payload == null) return HealthcareProtocol.SOAP;
        String local = local(payload);
        if (FHIR.equals(namespace) || local.equals("Bundle") && payload.toLowerCase().contains("fhir")) return HealthcareProtocol.FHIR_XML;
        if (HL7_V3.equals(namespace)) return local.equals("ClinicalDocument") ? HealthcareProtocol.CDA : HealthcareProtocol.HL7_V3;
        return HealthcareProtocol.SOAP;
    }
    private static SoapVersion soapVersion(String namespace) {
        for (SoapVersion candidate : SoapVersion.values()) if (candidate.namespace().equals(namespace)) return candidate;
        throw new SoapValidationException("Unsupported SOAP namespace: " + namespace);
    }
    private static String namespace(Map<String, String> attributes, String prefix) {
        String value = namespaceOrNull(attributes, prefix);
        if (value == null) throw new SoapValidationException("Missing SOAP namespace declaration for prefix '" + prefix
                + "' in " + attributes);
        return value;
    }
    private static String namespaceOrNull(Map<String, String> attributes, String prefix) {
        return attributes.get(prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix);
    }
    private static boolean samePrefix(String qname, String prefix) { return prefix(qname).equals(prefix); }
    private static String prefix(String qname) { int colon = qname.indexOf(':'); return colon < 0 ? "" : qname.substring(0, colon); }
    private static String local(String qname) { int colon = qname.indexOf(':'); return colon < 0 ? qname : qname.substring(colon + 1); }
    private HealthcareSoapInspector() { }
}
