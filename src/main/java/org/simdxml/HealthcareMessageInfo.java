package org.simdxml;

import java.util.Objects;

/** Allocation-bounded metadata projection used by the healthcare vertical fast path. */
public final class HealthcareMessageInfo implements VerticalMessageInfo {
    private final SoapVersion soapVersion; private final HealthcareProtocol protocol;
    private final String action, messageId, payloadName;
    public HealthcareMessageInfo(SoapVersion soapVersion, HealthcareProtocol protocol, String action, String messageId, String payloadName) {
        this.soapVersion = soapVersion; this.protocol = protocol; this.action = action; this.messageId = messageId; this.payloadName = payloadName;
    }
    public SoapVersion soapVersion() { return soapVersion; } public HealthcareProtocol protocol() { return protocol; }
    public String action() { return action; } public String messageId() { return messageId; } public String payloadName() { return payloadName; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof HealthcareMessageInfo)) return false; HealthcareMessageInfo that = (HealthcareMessageInfo) other; return soapVersion == that.soapVersion && protocol == that.protocol && Objects.equals(action, that.action) && Objects.equals(messageId, that.messageId) && Objects.equals(payloadName, that.payloadName); }
    @Override public int hashCode() { return Objects.hash(soapVersion, protocol, action, messageId, payloadName); }
    @Override public String toString() { return "HealthcareMessageInfo[soapVersion=" + soapVersion + ", protocol=" + protocol + ", action=" + action + ", messageId=" + messageId + ", payloadName=" + payloadName + "]"; }
}
