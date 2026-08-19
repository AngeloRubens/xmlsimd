package org.simdxml;

/** Parser-owned reusable storage; never escapes except as a callback-scoped read-only view. */
final class PaymentFlyweight implements PaymentFlyweightView {
    private PaymentProtocol protocol;
    private String messageType;
    private long transactionCount;
    private final XmlValueFlyweight messageId = new XmlValueFlyweight();
    private final XmlValueFlyweight controlSum = new XmlValueFlyweight();
    private final XmlValueFlyweight firstIban = new XmlValueFlyweight();
    private final XmlValueFlyweight firstBic = new XmlValueFlyweight();

    void clear() { protocol = PaymentProtocol.UNKNOWN; messageType = null; transactionCount = -1; messageId.clear(); controlSum.clear(); firstIban.clear(); firstBic.clear(); }
    void protocol(PaymentProtocol value) { protocol = value; } void messageType(String value) { messageType = value; }
    void transactionCount(long value) { transactionCount = value; }
    XmlValueFlyweight mutableValue(int id) {
        if (id == PaymentXmlTokens.MSG_ID) return messageId; if (id == PaymentXmlTokens.CTRL_SUM) return controlSum;
        if (id == PaymentXmlTokens.IBAN) return firstIban; if (id == PaymentXmlTokens.BICFI || id == PaymentXmlTokens.BIC) return firstBic;
        throw new IllegalArgumentException("Not a value field: " + id);
    }
    PaymentMessageInfo snapshot() { return new PaymentMessageInfo(protocol, messageType, messageId.value(), transactionCount, controlSum.value(), firstIban.value(), firstBic.value()); }
    @Override public PaymentProtocol protocol() { return protocol; } @Override public String messageType() { return messageType; }
    @Override public long transactionCount() { return transactionCount; } @Override public XmlValueView messageId() { return messageId; }
    @Override public XmlValueView controlSum() { return controlSum; } @Override public XmlValueView firstIban() { return firstIban; }
    @Override public XmlValueView firstBic() { return firstBic; }
}
