package org.simdxml;

/** Read-only FHIR view valid only for the duration of its parser callback. */
public interface FhirFlyweightView {
    String resourceType();
    String id();
}