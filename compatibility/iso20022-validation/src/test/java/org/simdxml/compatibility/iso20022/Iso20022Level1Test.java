package org.simdxml.compatibility.iso20022;

import org.junit.jupiter.api.Test;
import org.simdxml.PaymentMessageInfo;
import org.simdxml.PaymentProtocol;
import org.simdxml.SimdXmlParser;
import org.simdxml.VerticalProfile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Level 1 only: well-formedness, namespace/version routing and vertical projection. */
final class Iso20022Level1Test {
    @Test void routesPinnedPainPacsAndCamtFixturesByNamespaceAndMessage() throws Exception {
        verify("/payments/pain.001.001.09.xml", "pain.001.001.09", "CstmrCdtTrfInitn", PaymentProtocol.SEPA);
        verify("/payments/pacs.008.001.08.xml", "pacs.008.001.08", "FIToFICstmrCdtTrf", PaymentProtocol.ISO_20022);
        verify("/payments/camt.053.001.08.xml", "camt.053.001.08", "BkToCstmrStmt", PaymentProtocol.ISO_20022);
    }

    private void verify(String resource, String version, String message, PaymentProtocol protocol) throws Exception {
        byte[] xml = fixture(resource);
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        try {
            while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) { }
            assertEquals("Document", reader.getLocalName());
            assertTrue(reader.getNamespaceURI().endsWith(':' + version), reader.getNamespaceURI());
            while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT) { }
            assertEquals(message, reader.getLocalName());
        } finally { reader.close(); }
        SimdXmlParser parser = SimdXmlParser.builder().withCapacity(xml.length)
                .withVerticalProfile(VerticalProfile.PAYMENTS).build();
        PaymentMessageInfo info = (PaymentMessageInfo) parser.inspectVertical(xml);
        assertEquals(message, info.messageType());
        assertEquals(protocol, info.protocol());
    }
    private byte[] fixture(String name) throws IOException {
        InputStream input = getClass().getResourceAsStream(name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024]; int read;
        try { while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read); }
        finally { input.close(); }
        return output.toByteArray();
    }
}
