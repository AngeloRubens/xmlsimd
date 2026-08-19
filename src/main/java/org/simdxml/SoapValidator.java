package org.simdxml;

import java.util.List;

/** SOAP 1.1/1.2 envelope structure validation without network or external schema resolution. */
final class SoapValidator {
    static SoapMessage validate(XmlDocument document) {
        XmlElement envelope = document.root();
        QName rootName = QName.of(envelope.name());
        if (!rootName.local.equals("Envelope")) fail("Root element must be SOAP Envelope");
        String namespace = namespace(envelope, rootName.prefix);
        SoapVersion version = version(namespace);

        List<XmlElement> children = envelope.childElements();
        if (children.isEmpty() || children.size() > 2) fail("SOAP Envelope must contain optional Header and one Body");
        int at = 0;
        XmlElement header = null;
        if (soapElement(children.get(0), "Header", rootName.prefix)) header = children.get(at++);
        if (at >= children.size() || !soapElement(children.get(at), "Body", rootName.prefix))
            fail("SOAP Body is mandatory and must follow Header");
        XmlElement body = children.get(at++);
        if (at != children.size()) fail("Unexpected element after SOAP Body");
        return new SoapMessage(version, envelope, header, body);
    }

    private static boolean soapElement(XmlElement element, String local, String envelopePrefix) {
        QName name = QName.of(element.name());
        return name.local.equals(local) && name.prefix.equals(envelopePrefix);
    }
    private static String namespace(XmlElement element, String prefix) {
        String attribute = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        return element.attribute(attribute).orElseThrow(() ->
                new SoapValidationException("Missing namespace declaration for prefix '" + prefix + "'"));
    }
    private static SoapVersion version(String namespace) {
        for (SoapVersion version : SoapVersion.values()) if (version.namespace().equals(namespace)) return version;
        throw new SoapValidationException("Unsupported SOAP namespace: " + namespace);
    }
    private static void fail(String message) { throw new SoapValidationException(message); }
    private static final class QName {
        private final String prefix, local;
        private QName(String prefix, String local) { this.prefix = prefix; this.local = local; }
        static QName of(String value) {
            int colon = value.indexOf(':');
            return colon < 0 ? new QName("", value) : new QName(value.substring(0, colon), value.substring(colon + 1));
        }
    }
    private SoapValidator() { }
}
