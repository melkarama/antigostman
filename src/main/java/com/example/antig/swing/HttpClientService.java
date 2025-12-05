package com.example.antig.swing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpClientService {

    private final HttpClient httpClient;

    public HttpClientService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public HttpResponse<String> sendRequest(String url, String method, String body, java.util.Map<String, String> headers, long timeoutMillis, String httpVersion) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis));

        // Determine HTTP version
        HttpClient.Version version = HttpClient.Version.HTTP_1_1;
        if ("HTTP/2".equalsIgnoreCase(httpVersion)) {
            version = HttpClient.Version.HTTP_2;
        }
        builder.version(version);

        if (headers != null) {
            headers.forEach(builder::header);
        }

        // Default to GET if method is null
        String effectiveMethod = (method != null) ? method : "GET";
        
        switch (effectiveMethod.toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                builder.POST(HttpRequest.BodyPublishers.ofString(body));
                if (!headers.containsKey("Content-Type")) {
                    builder.header("Content-Type", "application/json");
                }
                break;
            case "PUT":
                builder.PUT(HttpRequest.BodyPublishers.ofString(body));
                if (!headers.containsKey("Content-Type")) {
                    builder.header("Content-Type", "application/json");
                }
                break;
            case "DELETE":
                builder.DELETE();
                break;
            case "PATCH":
                builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                if (!headers.containsKey("Content-Type")) {
                    builder.header("Content-Type", "application/json");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported method: " + effectiveMethod);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
