package org.simdxml;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HealthcareFormatsTest {
    @Test
    void extractsHl7MshFieldsAndSupportsTheConfiguredSeparator() {
        byte[] message = ("MSH*^~\\&*SENDING_APP*SENDING_FAC*RECEIVING_APP*RECEIVING_FAC*"
                + "20230101000000**ADT^A01*MSG00001*P*2.5\rPID*1").getBytes(StandardCharsets.UTF_8);
        SimdXmlParser parser = SimdXmlParser.builder().withCapacity(message.length)
                .withVerticalProfile(VerticalProfile.AUTO).build();

        Hl7v2MessageInfo info = (Hl7v2MessageInfo) parser.inspectVertical(message);

        assertEquals("SENDING_APP", info.sendingApplication());
        assertEquals("SENDING_FAC", info.sendingFacility());
        assertEquals("RECEIVING_APP", info.receivingApplication());
        assertEquals("RECEIVING_FAC", info.receivingFacility());
        assertEquals("20230101000000", info.dateTimeOfMessage());
        assertEquals("ADT^A01", info.messageType());
        assertEquals("MSG00001", info.messageControlId());
        assertEquals("P", info.processingId());
        assertEquals("2.5", info.versionId());
    }

    @Test
    void rejectsInputWithoutAnMshSegment() {
        SimdXmlParser parser = new SimdXmlParser(64, 16);
        assertThrows(XmlBindingException.class,
                () -> parser.inspectHl7v2("PID|1".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void extractsFhirPrimitiveIdAndAutoDetectsTheProfile() {
        byte[] message = ("<Patient xmlns='http://hl7.org/fhir'><id value='example'/></Patient>")
                .getBytes(StandardCharsets.UTF_8);
        SimdXmlParser parser = SimdXmlParser.builder().withCapacity(message.length)
                .withVerticalProfile(VerticalProfile.AUTO).build();

        assertEquals(new FhirMessageInfo("Patient", "example"), parser.inspectVertical(message));
        assertEquals("Patient:example", parser.withFhirFlyweight(message,
                view -> view.resourceType() + ":" + view.id()));
    }

    @Test
    void acceptsTextIdForNonCanonicalButCompatibleFhirInput() {
        byte[] message = "<Patient xmlns='http://hl7.org/fhir'><id>legacy</id></Patient>"
                .getBytes(StandardCharsets.UTF_8);
        SimdXmlParser parser = new SimdXmlParser(message.length, 16, true);
        assertEquals(new FhirMessageInfo("Patient", "legacy"), parser.inspectFhir(message));
    }
}
