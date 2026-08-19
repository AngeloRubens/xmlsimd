package org.simdxml;

final class HealthcareFlyweight implements HealthcareFlyweightView {
    private SoapVersion soapVersion; private HealthcareProtocol protocol; private String payloadName;
    private final XmlValueFlyweight action = new XmlValueFlyweight(), messageId = new XmlValueFlyweight();
    final XmlByteSlice capturedRaw = new XmlByteSlice();
    final StringBuilder splitText = new StringBuilder(96);
    void clear() { soapVersion = null; protocol = null; payloadName = null; action.clear(); messageId.clear(); splitText.setLength(0); }
    void metadata(SoapVersion version, HealthcareProtocol value, String payload) { soapVersion = version; protocol = value; payloadName = payload; }
    XmlValueFlyweight mutableAction() { return action; } XmlValueFlyweight mutableMessageId() { return messageId; }
    HealthcareMessageInfo snapshot() { return new HealthcareMessageInfo(soapVersion, protocol, action.value(), messageId.value(), payloadName); }
    @Override public SoapVersion soapVersion() { return soapVersion; } @Override public HealthcareProtocol protocol() { return protocol; }
    @Override public XmlValueView action() { return action; } @Override public XmlValueView messageId() { return messageId; }
    @Override public String payloadName() { return payloadName; }
}
