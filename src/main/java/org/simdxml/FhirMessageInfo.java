package org.simdxml;

import java.util.Objects;

/** Allocation-bounded projection of key fields from a FHIR XML document. */
public final class FhirMessageInfo implements VerticalMessageInfo {
    private final String resourceType;
    private final String id;

    public FhirMessageInfo(String resourceType, String id) {
        this.resourceType = resourceType;
        this.id = id;
    }

    public String resourceType() { return resourceType; }
    public String id() { return id; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FhirMessageInfo)) return false;
        FhirMessageInfo that = (FhirMessageInfo) other;
        return Objects.equals(resourceType, that.resourceType) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, id);
    }

    @Override
    public String toString() {
        return "FhirMessageInfo[resourceType=" + resourceType + ", id=" + id + "]";
    }
}