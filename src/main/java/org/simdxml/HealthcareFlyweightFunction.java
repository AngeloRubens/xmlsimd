package org.simdxml;

/** Scoped operation over a parser-owned SOAP/healthcare flyweight. */
@FunctionalInterface
public interface HealthcareFlyweightFunction<R> {
    R apply(HealthcareFlyweightView message);
}
