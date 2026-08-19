package org.simdxml;

/** Namespace URI plus local name; prefixes are intentionally not part of JAXB identity. */
final class XmlExpandedName {
    static final String EMPTY_NAMESPACE = "";
    private final String namespace;
    private final String localName;
    private final int hash;

    XmlExpandedName(String namespace, String localName) {
        this.namespace = namespace == null ? EMPTY_NAMESPACE : namespace;
        this.localName = java.util.Objects.requireNonNull(localName, "localName");
        this.hash = 31 * this.namespace.hashCode() + localName.hashCode();
    }

    String namespace() { return namespace; }
    String localName() { return localName; }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof XmlExpandedName)) return false;
        XmlExpandedName that = (XmlExpandedName) other;
        return namespace.equals(that.namespace) && localName.equals(that.localName);
    }
    @Override public String toString() {
        return namespace.isEmpty() ? localName : "{" + namespace + "}" + localName;
    }
}
