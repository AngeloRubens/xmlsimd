package org.simdxml.quarkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
final class QuarkusJaxbProviderTest {
    @TestHTTPResource("/provider") URL endpoint;

    @Test
    void quarkusEndpointUsesSimdxmlJaxbProvider() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("GET");
        assertEquals(200, connection.getResponseCode());
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }
        assertTrue(response.toString().contains("org.simdxml.jaxb.SimdJakartaContext"), response.toString());
    }
}
