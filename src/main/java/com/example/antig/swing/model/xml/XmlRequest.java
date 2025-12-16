package com.example.antig.swing.model.xml;

/**
 * XML-safe request node (leaf node).
 * Contains no children and no parent reference.
 */
public class XmlRequest extends XmlNode {
    private String method = "GET";
    private String url = "";
    private String body = "";
    private String params = "";

    public XmlRequest() {
        super();
    }

    public XmlRequest(String id, String name) {
        super(id, name);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    private String bodyType = "TEXT";

    public String getBodyType() {
        return bodyType;
    }

    public void setBodyType(String bodyType) {
        this.bodyType = bodyType;
    }

    private int executionTabIndex = 0;

    public int getExecutionTabIndex() {
        return executionTabIndex;
    }

    public void setExecutionTabIndex(int executionTabIndex) {
        this.executionTabIndex = executionTabIndex;
    }

    private long timeout = 1000;

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    private String httpVersion = "HTTP/1.1";

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }
    
    private boolean downloadContent = false;
    
    public boolean isDownloadContent() {
        return downloadContent;
    }
    
    public void setDownloadContent(boolean downloadContent) {
        this.downloadContent = downloadContent;
    }
}
