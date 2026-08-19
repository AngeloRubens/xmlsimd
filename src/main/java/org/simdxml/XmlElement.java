package org.simdxml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Element with document-order children and insertion-order attributes. */
public final class XmlElement implements XmlNode {
    private final String name;
    private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    private final ArrayList<XmlNode> children = new ArrayList<>();

    XmlElement(String name) { this.name = name; }
    void addAttribute(String name, String value) {
        if (attributes.putIfAbsent(name, value) != null) {
            throw new IllegalArgumentException("Duplicate attribute: " + name);
        }
    }
    void addChild(XmlNode child) { children.add(child); }

    @Override public Type type() { return Type.ELEMENT; }
    public String name() { return name; }
    public Map<String, String> attributes() { return Collections.unmodifiableMap(attributes); }
    public Optional<String> attribute(String name) { return Optional.ofNullable(attributes.get(name)); }
    public List<XmlNode> children() { return Collections.unmodifiableList(children); }
    public List<XmlElement> childElements() {
        List<XmlElement> result = new ArrayList<XmlElement>();
        for (XmlNode node : children) if (node instanceof XmlElement) result.add((XmlElement) node);
        return Collections.unmodifiableList(result);
    }
    public Optional<XmlElement> child(String name) {
        return childElements().stream().filter(e -> e.name.equals(name)).findFirst();
    }
    public String text() {
        StringBuilder out = new StringBuilder();
        appendText(this, out);
        return out.toString();
    }
    private static void appendText(XmlElement element, StringBuilder out) {
        for (XmlNode node : element.children) {
            if (node instanceof XmlText) out.append(((XmlText) node).value());
            else if (node instanceof XmlElement) appendText((XmlElement) node, out);
        }
    }
}
