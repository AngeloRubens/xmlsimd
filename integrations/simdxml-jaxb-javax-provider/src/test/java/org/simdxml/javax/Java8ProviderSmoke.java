package org.simdxml.javax;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Direct main used to prove provider discovery and round-trip on an actual Java 8 JRE. */
public final class Java8ProviderSmoke {
    public static void main(String[] args) throws Exception {
        JAXBContext context = JAXBContext.newInstance(Message.class);
        if (!context.getClass().getName().equals("org.simdxml.javax.SimdJakartaContext"))
            throw new AssertionError("Unexpected provider: " + context.getClass());
        Message source = new Message(); source.text = "Java EE 8";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        context.createMarshaller().marshal(source, output);
        Message result = (Message) context.createUnmarshaller().unmarshal(
                new ByteArrayInputStream(output.toByteArray()));
        if (!source.text.equals(result.text)) throw new AssertionError("Round-trip mismatch");
        System.out.println("simdxml javax provider Java 8 smoke: OK");
    }

    @XmlRootElement(name = "message")
    public static final class Message {
        @XmlElement public String text;
        public Message() { }
    }
    private Java8ProviderSmoke() { }
}
