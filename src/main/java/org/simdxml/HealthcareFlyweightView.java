package org.simdxml;

/** Read-only SOAP/WS-Addressing/HL7 view valid only during its parser callback. */
public interface HealthcareFlyweightView {
    SoapVersion soapVersion();
    HealthcareProtocol protocol();
    XmlValueView action();
    XmlValueView messageId();
    String payloadName();
}
