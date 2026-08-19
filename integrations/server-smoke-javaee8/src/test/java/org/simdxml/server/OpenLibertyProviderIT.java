package org.simdxml.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenLibertyProviderIT {
    @Test
    void javaEe8ContainerDiscoversSimdxmlAndRoundTrips() throws Exception {
        String port = System.getProperty("http.port", "19080");
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + "/simdxml-smoke/provider").openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        assertEquals(200, connection.getResponseCode());
        StringBuilder response = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) response.append(line).append('\n');
        } finally {
            reader.close();
            connection.disconnect();
        }
        String body = response.toString();
        assertTrue(body.contains("provider=org.simdxml."), body);
        assertTrue(body.contains("roundTrip=true"), body);
    }
}
