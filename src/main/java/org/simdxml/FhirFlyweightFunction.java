package org.simdxml;

/** Scoped operation over a parser-owned FHIR flyweight. */
@FunctionalInterface
public interface FhirFlyweightFunction<R> {
    R apply(FhirFlyweightView message);
}