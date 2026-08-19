package org.simdxml;

public enum SoapVersion {
    SOAP_1_1("http://schemas.xmlsoap.org/soap/envelope/"),
    SOAP_1_2("http://www.w3.org/2003/05/soap-envelope");

    private final String namespace;
    SoapVersion(String namespace) { this.namespace = namespace; }
    public String namespace() { return namespace; }
}
