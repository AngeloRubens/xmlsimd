package org.simdxml;

import java.util.Objects;

/** Allocation-bounded projection of routing/audit fields from a financial XML message. */
public final class PaymentMessageInfo implements VerticalMessageInfo {
    private final PaymentProtocol protocol; private final String messageType, messageId;
    private final long transactionCount; private final String controlSum, firstIban, firstBic;
    public PaymentMessageInfo(PaymentProtocol protocol, String messageType, String messageId, long transactionCount,
            String controlSum, String firstIban, String firstBic) {
        this.protocol = protocol; this.messageType = messageType; this.messageId = messageId;
        this.transactionCount = transactionCount; this.controlSum = controlSum; this.firstIban = firstIban; this.firstBic = firstBic;
    }
    public PaymentProtocol protocol() { return protocol; } public String messageType() { return messageType; }
    public String messageId() { return messageId; } public long transactionCount() { return transactionCount; }
    public String controlSum() { return controlSum; } public String firstIban() { return firstIban; } public String firstBic() { return firstBic; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof PaymentMessageInfo)) return false; PaymentMessageInfo that = (PaymentMessageInfo) other; return transactionCount == that.transactionCount && protocol == that.protocol && Objects.equals(messageType, that.messageType) && Objects.equals(messageId, that.messageId) && Objects.equals(controlSum, that.controlSum) && Objects.equals(firstIban, that.firstIban) && Objects.equals(firstBic, that.firstBic); }
    @Override public int hashCode() { return Objects.hash(protocol, messageType, messageId, transactionCount, controlSum, firstIban, firstBic); }
    @Override public String toString() { return "PaymentMessageInfo[protocol=" + protocol + ", messageType=" + messageType + ", messageId=" + messageId + ", transactionCount=" + transactionCount + ", controlSum=" + controlSum + ", firstIban=" + firstIban + ", firstBic=" + firstBic + "]"; }
}
