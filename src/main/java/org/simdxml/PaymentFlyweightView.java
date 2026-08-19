package org.simdxml;

/** Read-only ISO 20022 view valid only for the duration of its parser callback. */
public interface PaymentFlyweightView {
    PaymentProtocol protocol();
    String messageType();
    long transactionCount();
    XmlValueView messageId();
    XmlValueView controlSum();
    XmlValueView firstIban();
    XmlValueView firstBic();
}
