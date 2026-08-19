package org.simdxml.compatibility.healthcare;

import org.junit.jupiter.api.Test;
import org.simdxml.HealthcareMessageInfo;
import org.simdxml.HealthcareProtocol;
import org.simdxml.SimdXmlParser;
import org.simdxml.SoapValidationException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Routing/structural gate only; it does not establish clinical-profile conformance. */
final class HealthcareRoutingGateTest {
    @Test void cdaAndFhirSoapPayloadsMatchFastAndGenericRoutes() {
        verify(soap12("<ClinicalDocument xmlns='urn:hl7-org:v3'><id root='1.2.3'/></ClinicalDocument>"),
                HealthcareProtocol.CDA, "ClinicalDocument");
        verify(soap12("<Bundle xmlns='http://hl7.org/fhir'><id value='example'/><type value='collection'/></Bundle>"),
                HealthcareProtocol.FHIR_XML, "Bundle");
        verify(soap12("<PRPA_IN201305UV02 xmlns='urn:hl7-org:v3'><id root='1.2.3'/></PRPA_IN201305UV02>"),
                HealthcareProtocol.HL7_V3, "PRPA_IN201305UV02");
    }

    @Test void malformedSoapIsRejectedByFastAndGenericRoutes() {
        final byte[] missingBody = ("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope'>"
                + "<s:Header/></s:Envelope>").getBytes(StandardCharsets.UTF_8);
        assertThrows(SoapValidationException.class, () -> parser(missingBody, true).inspectHealthcare(missingBody));
        assertThrows(SoapValidationException.class, () -> parser(missingBody, false).inspectHealthcare(missingBody));
    }

    private static void verify(byte[] xml, HealthcareProtocol protocol, String payload) {
        HealthcareMessageInfo fast = parser(xml, true).inspectHealthcare(xml);
        HealthcareMessageInfo generic = parser(xml, false).inspectHealthcare(xml);
        assertEquals(generic, fast);
        assertEquals(protocol, fast.protocol());
        assertEquals(payload, fast.payloadName());
    }
    private static SimdXmlParser parser(byte[] xml, boolean vertical) {
        return new SimdXmlParser(xml.length, 64, vertical);
    }
    private static byte[] soap12(String payload) {
        return ("<s:Envelope xmlns:s='http://www.w3.org/2003/05/soap-envelope' "
                + "xmlns:wsa='http://www.w3.org/2005/08/addressing'><s:Header>"
                + "<wsa:Action>urn:test</wsa:Action><wsa:MessageID>urn:uuid:test</wsa:MessageID>"
                + "</s:Header><s:Body>" + payload + "</s:Body></s:Envelope>").getBytes(StandardCharsets.UTF_8);
    }
}
