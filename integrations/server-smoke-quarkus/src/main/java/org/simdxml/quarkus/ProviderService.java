package org.simdxml.quarkus;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/provider")
public class ProviderService {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String provider() throws Exception {
        return JAXBContext.newInstance(ProbeModel.class).getClass().getName();
    }

    @XmlRootElement
    public static final class ProbeModel {
        public String value;
        public ProbeModel() { }
    }
}
