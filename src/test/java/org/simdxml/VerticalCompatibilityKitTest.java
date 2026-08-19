package org.simdxml;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent semantic A/B gate for simdxml vertical accelerators. */
final class VerticalCompatibilityKitTest {
    @Test void soap11Soap12AndHl7MatchGenericFixedAndAuto() throws Exception {
        byte[][] messages = { fixture("/soap/standard-soap11.xml"), fixture("/healthcare/ihe-xcpd-soap12.xml") };
        for (byte[] message : messages) {
            HealthcareMessageInfo generic = parser(message, VerticalProfile.NONE).inspectHealthcare(message);
            HealthcareMessageInfo fast = parser(message, VerticalProfile.SOAP_HEALTHCARE).inspectHealthcare(message);
            assertEquals(generic, fast);
            assertEquals(fast, parser(message, VerticalProfile.SOAP_HEALTHCARE).inspectVertical(message));
            assertEquals(fast, parser(message, VerticalProfile.AUTO).inspectVertical(message));
        }
        assertEquals(HealthcareProtocol.SOAP,
                parser(messages[0], VerticalProfile.AUTO).inspectHealthcare(messages[0]).protocol());
        assertEquals(HealthcareProtocol.HL7_V3,
                parser(messages[1], VerticalProfile.AUTO).inspectHealthcare(messages[1]).protocol());
    }

    @Test void painPacsAndCamtMatchGenericFixedAndAuto() throws Exception {
        byte[][] messages = { fixture("/payments/pain.001.001.09.xml"), fixture("/payments/pacs.008.001.08.xml"),
                fixture("/payments/camt.053.001.08.xml") };
        String[] expectedTypes = { "CstmrCdtTrfInitn", "FIToFICstmrCdtTrf", "BkToCstmrStmt" };
        for (int i = 0; i < messages.length; i++) {
            byte[] message = messages[i];
            PaymentMessageInfo generic = parser(message, VerticalProfile.NONE).inspectPayment(message);
            PaymentMessageInfo fast = parser(message, VerticalProfile.PAYMENTS).inspectPayment(message);
            assertEquals(generic, fast);
            assertEquals(expectedTypes[i], fast.messageType());
            assertEquals(fast, parser(message, VerticalProfile.PAYMENTS).inspectVertical(message));
            assertEquals(fast, parser(message, VerticalProfile.AUTO).inspectVertical(message));
        }
    }

    @Test void builderAndJvmPropertiesSelectWithoutChangingSemantics() throws Exception {
        byte[] payment = fixture("/payments/pacs.008.001.08.xml");
        byte[] soap = fixture("/soap/standard-soap11.xml");
        String oldProfile = System.getProperty("org.simdxml.vertical.profile");
        String oldEnabled = System.getProperty("org.simdxml.vertical.enabled");
        try {
            System.setProperty("org.simdxml.vertical.enabled", "true");
            System.setProperty("org.simdxml.vertical.profile", "payments");
            SimdXmlParser propertyPayment = SimdXmlParser.builder().withCapacity(payment.length).build();
            assertEquals(VerticalProfile.PAYMENTS, propertyPayment.verticalProfile());
            assertEquals(parser(payment, VerticalProfile.PAYMENTS).inspectPayment(payment), propertyPayment.inspectVertical(payment));

            System.setProperty("org.simdxml.vertical.profile", "soap");
            SimdXmlParser propertySoap = SimdXmlParser.builder().withCapacity(soap.length).build();
            assertEquals(VerticalProfile.SOAP_HEALTHCARE, propertySoap.verticalProfile());
            assertEquals(parser(soap, VerticalProfile.SOAP_HEALTHCARE).inspectHealthcare(soap), propertySoap.inspectVertical(soap));

            System.setProperty("org.simdxml.vertical.enabled", "false");
            assertEquals(VerticalProfile.NONE, SimdXmlParser.builder().withCapacity(soap.length).build().verticalProfile());
        } finally {
            restore("org.simdxml.vertical.profile", oldProfile);
            restore("org.simdxml.vertical.enabled", oldEnabled);
        }
    }

    @Test void threadSafeAutoDispatchHasNoCrossFormatStateLeak() throws Exception {
        final byte[] payment = fixture("/payments/camt.053.001.08.xml");
        final byte[] soap = fixture("/healthcare/ihe-xcpd-soap12.xml");
        final ThreadSafeSimdXmlParser shared = SimdXmlParser.builder()
                .withCapacity(Math.max(payment.length, soap.length)).withVerticalProfile(VerticalProfile.AUTO).buildThreadSafe();
        final PaymentMessageInfo expectedPayment = parser(payment, VerticalProfile.PAYMENTS).inspectPayment(payment);
        final HealthcareMessageInfo expectedSoap = parser(soap, VerticalProfile.SOAP_HEALTHCARE).inspectHealthcare(soap);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 64; i++) {
                final boolean payments = (i & 1) == 0;
                tasks.add(new Callable<Boolean>() {
                    @Override public Boolean call() {
                        return Boolean.valueOf(payments ? expectedPayment.equals(shared.inspectVertical(payment))
                                : expectedSoap.equals(shared.inspectVertical(soap)));
                    }
                });
            }
            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            for (Future<Boolean> future : futures) assertEquals(Boolean.TRUE, future.get());
        } finally { executor.shutdownNow(); }
    }

    private static SimdXmlParser parser(byte[] input, VerticalProfile profile) {
        return SimdXmlParser.builder().withCapacity(input.length).withMaxDepth(128).withVerticalProfile(profile).build();
    }
    private byte[] fixture(String name) throws IOException { return getClass().getResourceAsStream(name).readAllBytes(); }
    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name); else System.setProperty(name, value);
    }
}
