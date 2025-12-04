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
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public HttpResponse<String> sendRequest(String url, String method, String body, java.util.Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1));

        if (headers != null) {
            headers.forEach(builder::header);
        }

        switch (method.toUpperCase()) {
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
                throw new IllegalArgumentException("Unsupported method: " + method);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
