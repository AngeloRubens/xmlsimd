package org.simdxml.quarkus;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.xml.bind.JAXBContext;

@WebService(serviceName = "ProviderService", targetNamespace = "urn:simdxml:test")
public class SoapProviderService {
    @WebMethod
    public String provider() throws Exception {
        return JAXBContext.newInstance(ProviderService.ProbeModel.class).getClass().getName();
    }
}
