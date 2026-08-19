package org.simdxml.server;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TomcatProviderIT {
    @Test
    void packagedWarDiscoversSimdxmlInsideTomcat9() throws Exception {
        File war = new File("target/simdxml-javaee8-smoke.war").getCanonicalFile();
        File base = new File("target/tomcat-it").getCanonicalFile();
        File webapps = new File(base, "webapps");
        if (!webapps.isDirectory() && !webapps.mkdirs()) throw new IllegalStateException("Cannot create " + webapps);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.getPath());
        tomcat.getHost().setAppBase(webapps.getPath());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context context = tomcat.addWebapp("/simdxml-smoke", war.getPath());
        context.setParentClassLoader(getClass().getClassLoader());
        try {
            tomcat.start();
            HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:"
                    + tomcat.getConnector().getLocalPort() + "/simdxml-smoke/provider").openConnection();
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
            assertTrue(body.contains("providerSource=") && body.contains("WEB-INF/lib"), body);
            assertTrue(body.contains("roundTrip=true"), body);
        } finally {
            try { tomcat.stop(); } finally { tomcat.destroy(); }
        }
    }
}
