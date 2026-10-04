package org.simdxml.server.cxf;

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
 * Tests that the simdxml CXF DataBinding is selected when deployed in Jetty.
 */
final class JettyCxfProviderIT {
    @Test
    void packagedWarDiscoversSimdxmlInsideJetty() throws Exception {
        // Similar to Tomcat test but for Jetty
        Server server = new Server(0); // Use port 0 for random available port
        WebAppContext webapp = new WebAppContext();
        webapp.setWar("target/simdxml-cxf-smoke.war");
        webapp.setContextPath("/simdxml-cxf-smoke");
        server.setHandler(webapp);

        try {
            server.start();
            int port = server.getURI().getPort();
            HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:"
                    + port + "/simdxml-cxf-smoke/provider").openConnection();
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
            assertTrue(body.contains("provider=org.simdxml.cxf.SimdXmlDataBinding"), body);
            assertTrue(body.contains("providerSource=") && body.contains("WEB-INF/lib"), body);
            assertTrue(body.contains("roundTrip=true"), body);
        } finally {
            try { server.stop(); } finally { server.destroy(); }
        }
    }
}