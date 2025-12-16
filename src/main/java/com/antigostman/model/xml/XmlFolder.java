package com.antigostman.model.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * XML-safe folder node.
 * Contains only children references, no parent reference.
 */
public class XmlFolder extends XmlNode {
    private List<XmlNode> children = new ArrayList<>();

    public XmlFolder() {
        super();
    }

    public XmlFolder(String id, String name) {
        super(id, name);
    }

    public List<XmlNode> getChildren() {
        return children;
    }

    public void setChildren(List<XmlNode> children) {
        this.children = children;
    }
}
