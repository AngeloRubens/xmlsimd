package org.simdxml.quarkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
final class QuarkusCxfProviderTest {
    @TestHTTPResource("/soap/provider") URL endpoint;

    @Test
    void cxfSoapEndpointUsesSimdxmlJaxbProvider() throws Exception {
        String envelope = "<soap:Envelope xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/' "
                + "xmlns:t='urn:simdxml:test'><soap:Body><t:provider/></soap:Body></soap:Envelope>";
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(envelope.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, connection.getResponseCode());
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }
        assertTrue(response.toString().contains("org.simdxml.jaxb.SimdJakartaContext"), response.toString());
    }
}
