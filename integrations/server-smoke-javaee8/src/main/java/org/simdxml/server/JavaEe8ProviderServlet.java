package org.simdxml.server;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Tests provider selection from inside a Java EE request, not from the Maven test JVM. */
@WebServlet(urlPatterns = "/provider")
public final class JavaEe8ProviderServlet extends HttpServlet {
    private static final JAXBContext CONTEXT = createContext();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        try {
            Message input = new Message();
            input.text = "Java EE 8";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Marshaller marshaller = CONTEXT.createMarshaller();
            marshaller.marshal(input, bytes);
            Unmarshaller unmarshaller = CONTEXT.createUnmarshaller();
            Message output = (Message) unmarshaller.unmarshal(new ByteArrayInputStream(bytes.toByteArray()));
            boolean simdxml = CONTEXT.getClass().getName().startsWith("org.simdxml.");
            boolean roundTrip = input.text.equals(output.text);
            response.setStatus(simdxml && roundTrip ? 200 : 500);
            response.getWriter().print("provider=" + CONTEXT.getClass().getName()
                    + "\nproviderSource=" + CONTEXT.getClass().getProtectionDomain().getCodeSource().getLocation()
                    + "\nroundTrip=" + roundTrip
                    + "\nxml=" + new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception failure) {
            response.setStatus(500);
            failure.printStackTrace(response.getWriter());
        }
    }

    private static JAXBContext createContext() {
        try {
            return JAXBContext.newInstance(Message.class);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @XmlRootElement(name = "message")
    public static final class Message {
        @XmlElement public String text;
        public Message() { }
    }
}
