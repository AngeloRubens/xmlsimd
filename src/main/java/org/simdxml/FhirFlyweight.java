package org.simdxml;

/** Parser-owned reusable storage; never escapes except as a callback-scoped read-only view. */
final class FhirFlyweight implements FhirFlyweightView {
    private String resourceType;
    private String id;

    void clear() { resourceType = null; id = null; }

    void resourceType(String value) { resourceType = value; }
    void id(String value) { id = value; }

    FhirMessageInfo snapshot() { return new FhirMessageInfo(resourceType, id); }

    @Override public String resourceType() { return resourceType; }
    @Override public String id() { return id; }
}