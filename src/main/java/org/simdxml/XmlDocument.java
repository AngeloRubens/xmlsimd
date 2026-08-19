package org.simdxml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class XmlDocument {
    private final XmlElement root; private final List<XmlNode> prolog, epilog;
    public XmlDocument(XmlElement root, List<XmlNode> prolog, List<XmlNode> epilog) {
        this.root = root;
        this.prolog = Collections.unmodifiableList(new ArrayList<XmlNode>(prolog));
        this.epilog = Collections.unmodifiableList(new ArrayList<XmlNode>(epilog));
    }
    public XmlElement root() { return root; } public List<XmlNode> prolog() { return prolog; } public List<XmlNode> epilog() { return epilog; }
    @Override public boolean equals(Object other) { if (this == other) return true; if (!(other instanceof XmlDocument)) return false; XmlDocument that = (XmlDocument) other; return Objects.equals(root, that.root) && Objects.equals(prolog, that.prolog) && Objects.equals(epilog, that.epilog); }
    @Override public int hashCode() { return Objects.hash(root, prolog, epilog); }
    @Override public String toString() { return "XmlDocument[root=" + root + ", prolog=" + prolog + ", epilog=" + epilog + "]"; }
}
