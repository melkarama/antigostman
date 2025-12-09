package com.example.antig.swing.model.xml;

import java.util.ArrayList;
import java.util.List;

/**
 * XML-safe collection node.
 * Contains only children references, no parent reference.
 */
public class XmlCollection extends XmlNode {
    private List<XmlNode> children = new ArrayList<>();
    private String lastSelectedNodeId;
    @com.fasterxml.jackson.annotation.JsonProperty("globalVariables")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private java.util.Map<String, String> globalVariables = new java.util.LinkedHashMap<>();

    public XmlCollection() {
        super();
    }

    public XmlCollection(String id, String name) {
        super(id, name);
    }

    public List<XmlNode> getChildren() {
        return children;
    }

    public void setChildren(List<XmlNode> children) {
        this.children = children;
    }

    public String getLastSelectedNodeId() {
        return lastSelectedNodeId;
    }

    public void setLastSelectedNodeId(String lastSelectedNodeId) {
        this.lastSelectedNodeId = lastSelectedNodeId;
    }

    public java.util.Map<String, String> getGlobalVariables() {
        return globalVariables;
    }

    public void setGlobalVariables(java.util.Map<String, String> globalVariables) {
        this.globalVariables = globalVariables;
    }
}
