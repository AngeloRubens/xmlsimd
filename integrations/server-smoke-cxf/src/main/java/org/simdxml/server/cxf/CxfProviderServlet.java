package org.simdxml.server.cxf;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.simdxml.SimdJaxbContext;
import org.simdxml.cxf.SimdXmlDataBinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Packaged-WAR probe for the CXF bridge and its simdxml runtime. */
@WebServlet("/provider")
public final class CxfProviderServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SimdJaxbContext context = SimdJaxbContext.newInstance(Probe.class);
        Probe output = context.unmarshal("<probe><text>ok</text></probe>".getBytes(StandardCharsets.UTF_8), Probe.class);
        boolean roundTrip = "ok".equals(output.text);
        Class<?> provider = SimdXmlDataBinding.class;
        response.setStatus(roundTrip ? 200 : 500);
        response.setContentType("text/plain");
        response.getWriter().write("provider=" + provider.getName()
                + "\nproviderSource=" + provider.getProtectionDomain().getCodeSource().getLocation()
                + "\nroundTrip=" + roundTrip + "\n");
    }

    @XmlRootElement(name = "probe")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Probe {
        public String text;
        public Probe() { }
    }
}
