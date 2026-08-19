package org.simdxml;

/** Scoped operation over a parser-owned ISO 20022 flyweight. */
@FunctionalInterface
public interface PaymentFlyweightFunction<R> {
    R apply(PaymentFlyweightView payment);
}
