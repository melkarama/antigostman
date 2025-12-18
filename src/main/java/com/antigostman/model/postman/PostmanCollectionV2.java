package com.antigostman.model.postman;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Postman Collection v2.1 JSON structure
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostmanCollectionV2 {
    private Info info;
    private List<Item> item = new ArrayList<>();
    private List<Variable> variable = new ArrayList<>();
    private Auth auth;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {
        private String name;
        
        @JsonProperty("_postman_id")
        private String postmanId;
        
        private Object description; // Can be String or Description object
        private String schema;
        private Version version;
        
        public String getDescriptionAsString() {
            return extractDescription(description);
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Version {
        private String major;
        private String minor;
        private String patch;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;
        private String name;
        private Object description; // Can be String or Description object
        private Request request;
        private List<Response> response = new ArrayList<>();
        private List<Event> event = new ArrayList<>();
        
        // For folders (item groups)
        private List<Item> item = new ArrayList<>();
        
        public boolean isFolder() {
            return item != null && !item.isEmpty();
        }
        
        public String getDescriptionAsString() {
            return extractDescription(description);
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Request {
        private Object url; // Can be string or UrlObject
        private String method;
        private List<Header> header = new ArrayList<>();
        private Body body;
        private Object description; // Can be String or Description object
        private Auth auth;
        
        public String getUrlAsString() {
            if (url instanceof String) {
                return (String) url;
            } else if (url instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> urlMap = (java.util.Map<String, Object>) url;
                Object raw = urlMap.get("raw");
                if (raw != null) {
                    return raw.toString();
                }
            }
            return "";
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String key;
        private String value;
        private Object description; // Can be String or Description object
        private boolean disabled;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        private String mode; // raw, urlencoded, formdata, file, graphql
        private String raw;
        private List<FormParameter> urlencoded = new ArrayList<>();
        private List<FormParameter> formdata = new ArrayList<>();
        private Options options;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Options {
            private RawOptions raw;
            
            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class RawOptions {
                private String language; // json, xml, text, javascript, html
            }
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FormParameter {
        private String key;
        private String value;
        private Object description; // Can be String or Description object
        private String type; // text or file
        private String src; // file path for file type
        private boolean disabled;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private String id;
        private String name;
        private Request originalRequest;
        private String status;
        private int code;
        private List<Header> header = new ArrayList<>();
        private String body;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        private String listen; // prerequest, test
        private Script script;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Script {
            private String id;
            private List<String> exec = new ArrayList<>();
            private String type; // text/javascript
            
            public String getExecutionScript() {
                if (exec != null && !exec.isEmpty()) {
                    return String.join("\n", exec);
                }
                return "";
            }
        }
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Variable {
        private String id;
        private String key;
        private String value;
        private String type;
        private Object description; // Can be String or Description object
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Auth {
        private String type; // basic, bearer, apikey, etc.
        
        private Object basic;
        private Object bearer;
        private Object apikey;
        private Object awsv4;
        private Object digest;
        private Object oauth1;
        private Object oauth2;
        private Object ntlm;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class AuthAttribute {
            private String key;
            private Object value;
            private String type;
        }
    }
    
    /**
     * Helper method to extract description string from either String or Object format
     */
    private static String extractDescription(Object description) {
        if (description == null) {
            return null;
        }
        if (description instanceof String) {
            return (String) description;
        }
        if (description instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> descMap = (java.util.Map<String, Object>) description;
            Object content = descMap.get("content");
            if (content != null) {
                return content.toString();
            }
        }
        return null;
    }
}
