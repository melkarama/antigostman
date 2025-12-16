package com.antigostman;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpClientService {

	private final HttpClient httpClient;

	public HttpClientService() throws KeyManagementException, NoSuchAlgorithmException {
		this.httpClient = HttpClient.newBuilder().sslContext(createTrustAllSslContext())
				.version(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10)).build();
	}

	public HttpResponse<String> sendRequest(String url, String method, String body,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion) throws Exception {
		HttpRequest builder = buildRequest(url, method, body, headers, timeoutMillis, httpVersion);
		return httpClient.send(builder, HttpResponse.BodyHandlers.ofString());
	}

	public HttpResponse<byte[]> sendRequestBytes(String url, String method, String body,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion) throws Exception {
		HttpRequest builder = buildRequest(url, method, body, headers, timeoutMillis, httpVersion);
		return httpClient.send(builder, HttpResponse.BodyHandlers.ofByteArray());
	}

	private HttpRequest buildRequest(String url, String method, String body,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion) {

		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url))
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
		return builder.build();
	}

	public static SSLContext createTrustAllSslContext() throws NoSuchAlgorithmException, KeyManagementException {
		// Trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) {
				/* no-op */ }

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) {
				/* no-op */ }

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		} };

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, trustAllCerts, new SecureRandom());
		return sslContext;
	}

}
