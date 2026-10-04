package org.simdxml;

/** Streaming FHIR XML projection that materializes only returned audit/routing fields. */
final class FhirXmlInspector {
    private final SimdXmlParser parser;
    private final StringBuilder splitText = new StringBuilder(64);
    private final FhirFlyweight flyweight = new FhirFlyweight();

    FhirXmlInspector(SimdXmlParser parser) { this.parser = parser; }

    FhirMessageInfo inspect(byte[] input, boolean vertical) {
        fill(input, vertical, flyweight);
        return flyweight.snapshot();
    }

    <R> R withFlyweight(byte[] input, boolean vertical, FhirFlyweightFunction<R> operation) {
        fill(input, vertical, flyweight);
        return operation.apply(flyweight);
    }

    private void fill(byte[] input, boolean vertical, FhirFlyweight out) {
        SimdXmlStreamReader reader = parser.reusableStream(input);
        int depth = 0;
        boolean captureIdText = false;
        splitText.setLength(0);
        out.clear();

        while (reader.hasNext()) {
            XmlEvent event = reader.next();
            if (event == XmlEvent.START_ELEMENT) {
                depth++;
                if (depth == 1) {
                    out.resourceType(localName(reader.name()));
                } else if (depth == 2 && out.id() == null && field(reader, vertical) == FhirXmlTokens.ID) {
                    String value = reader.attribute("value");
                    if (value != null) out.id(value.trim());
                    else {
                        captureIdText = true;
                        splitText.setLength(0);
                    }
                }
            } else if ((event == XmlEvent.TEXT || event == XmlEvent.CDATA) && captureIdText) {
                splitText.append(reader.text());
            } else if (event == XmlEvent.END_ELEMENT) {
                if (captureIdText && depth == 2 && field(reader, vertical) == FhirXmlTokens.ID) {
                    out.id(splitText.toString().trim());
                    captureIdText = false;
                }
                depth--;
            }
        }
    }

    private static int field(SimdXmlStreamReader reader, boolean vertical) {
        if (vertical) return FhirXmlTokens.field(reader);
        return "id".equals(localName(reader.name())) ? FhirXmlTokens.ID : FhirXmlTokens.NONE;
    }

    private static String localName(String name) {
        int colon = name.lastIndexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }
}
