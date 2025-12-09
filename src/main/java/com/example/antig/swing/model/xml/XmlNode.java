package com.example.antig.swing.model.xml;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for XML-safe node representation.
 * Contains NO parent reference to avoid cyclic dependencies during XML marshalling/unmarshalling.
 * Only stores children references (downward in the tree).
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = XmlCollection.class, name = "collection"),
        @JsonSubTypes.Type(value = XmlFolder.class, name = "folder"),
        @JsonSubTypes.Type(value = XmlRequest.class, name = "request")
})
public abstract class XmlNode {
    private String id;
    private String name;
    private Map<String, String> environment = new LinkedHashMap<>();
    private Map<String, String> headers = new LinkedHashMap<>();
    private String prescript = "";
    private String postscript = "";
    private boolean expanded = false; // Track if node was expanded in UI
    private int selectedTabIndex = 0;

    public XmlNode() {
    }

    public XmlNode(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getPrescript() {
        return prescript;
    }

    public void setPrescript(String prescript) {
        this.prescript = prescript;
    }

    public String getPostscript() {
        return postscript;
    }

    public void setPostscript(String postscript) {
        this.postscript = postscript;
    }
    
    public boolean isExpanded() {
        return expanded;
    }
    
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }

    public void setSelectedTabIndex(int selectedTabIndex) {
        this.selectedTabIndex = selectedTabIndex;
    }
}
