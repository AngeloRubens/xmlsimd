package org.simdxml.server.axis2;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the simdxml Axis2 DataBinding is selected when deployed in Jetty.
 */
final class JettyAxis2ProviderIT {
    @Test
    void packagedWarDiscoversSimdxmlInsideJetty() throws Exception {
        // Similar to Tomcat test but for Jetty
        Server server = new Server(0); // Use port 0 for random available port
        WebAppContext webapp = new WebAppContext();
        webapp.setWar("target/simdxml-axis2-smoke.war");
        webapp.setContextPath("/simdxml-axis2-smoke");
        server.setHandler(webapp);

        try {
            server.start();
            int port = server.getURI().getPort();
            HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:"
                    + port + "/simdxml-axis2-smoke/provider").openConnection();
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
            assertTrue(body.contains("provider=org.simdxml.axis2.SimdXmlDataBinding"), body);
            assertTrue(body.contains("providerSource=") && body.contains("WEB-INF/lib"), body);
            assertTrue(body.contains("roundTrip=true"), body);
        } finally {
            try { server.stop(); } finally { server.destroy(); }
        }
    }
}