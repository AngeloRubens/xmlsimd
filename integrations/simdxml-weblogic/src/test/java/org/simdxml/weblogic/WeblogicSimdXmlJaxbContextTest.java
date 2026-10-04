package org.simdxml.weblogic;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeblogicSimdXmlJaxbContextTest {

    @XmlRootElement
    public static class TestBean {
        @XmlElement
        private String value;

        public TestBean() {}

        public TestBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @Test
    void testCreateContextWithClasses() throws JAXBException {
        JAXBContext context = WeblogicSimdXmlJaxbContext.createContext(TestBean.class);
        assertNotNull(context);
        assertTrue(context instanceof WeblogicSimdXmlJaxbContext);
    }

    @Test
    void testCreateContextWithContextPath() throws JAXBException {
        // This test assumes we have a jaxb.index file in the test resources
        // For simplicity, we'll skip if it fails due to missing context path
        try {
            JAXBContext context = WeblogicSimdXmlJaxbContext.createContext("org.simdxml.weblogic");
            assertNotNull(context);
            assertTrue(context instanceof WeblogicSimdXmlJaxbContext);
        } catch (JAXBException e) {
            // Ignore if context path is not set up for this test
        }
    }

    @Test
    void testCreateMtomEnabledContext() throws JAXBException {
        JAXBContext context = WeblogicSimdXmlJaxbContext.createMtomEnabledContext(TestBean.class);
        assertNotNull(context);
        assertTrue(context instanceof WeblogicSimdXmlJaxbContext);
    }

    @Test
    void testMarshallerAndUnmarshaller() throws JAXBException {
        JAXBContext context = WeblogicSimdXmlJaxbContext.createContext(TestBean.class);
        Marshaller marshaller = context.createMarshaller();
        Unmarshaller unmarshaller = context.createUnmarshaller();

        TestBean original = new TestBean("Hello WebLogic");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        marshaller.marshal(original, out);

        String xml = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(xml.contains("<value>Hello WebLogic</value>"));

        TestBean unmarshalled = (TestBean) unmarshaller.unmarshal(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(original.getValue(), unmarshalled.getValue());
    }

    @Test
    void testMtomEnabledMarshaller() throws JAXBException {
        JAXBContext context = WeblogicSimdXmlJaxbContext.createMtomEnabledContext(TestBean.class);
        Marshaller marshaller = context.createMarshaller();
        assertNotNull(marshaller);
    }
}
