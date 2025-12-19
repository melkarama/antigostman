package com.antigostman.service;

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
		HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(body);
		// Let buildRequest handle the defaults, but we need to pass the publisher
		return sendRequest(url, method, publisher, headers, timeoutMillis, httpVersion, null);
	}

	public HttpResponse<String> sendRequest(String url, String method, HttpRequest.BodyPublisher bodyPublisher,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion, String contentType)
			throws Exception {
		HttpRequest builder = buildRequest(url, method, bodyPublisher, headers, timeoutMillis, httpVersion, contentType);
		return httpClient.send(builder, HttpResponse.BodyHandlers.ofString());
	}

	public HttpResponse<byte[]> sendRequestBytes(String url, String method, String body,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion) throws Exception {
		HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(body);
		return sendRequestBytes(url, method, publisher, headers, timeoutMillis, httpVersion, null);
	}

	public HttpResponse<byte[]> sendRequestBytes(String url, String method, HttpRequest.BodyPublisher bodyPublisher,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion, String contentType)
			throws Exception {
		HttpRequest builder = buildRequest(url, method, bodyPublisher, headers, timeoutMillis, httpVersion, contentType);
		return httpClient.send(builder, HttpResponse.BodyHandlers.ofByteArray());
	}

	private HttpRequest buildRequest(String url, String method, HttpRequest.BodyPublisher bodyPublisher,
			java.util.Map<String, String> headers, long timeoutMillis, String httpVersion, String contentType) {

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

		// Explicit Content-Type if provided (overrides header map if conflict, or adds
		// if missing)
		if (contentType != null) {
			builder.setHeader("Content-Type", contentType);
		}

		// Default to GET if method is null
		String effectiveMethod = (method != null) ? method : "GET";

		switch (effectiveMethod.toUpperCase()) {
		case "GET":
			builder.GET();
			break;
		case "POST":
			builder.POST(bodyPublisher);
			if (contentType == null && headers != null && !headers.containsKey("Content-Type")) {
				builder.header("Content-Type", "application/json");
			}
			break;
		case "PUT":
			builder.PUT(bodyPublisher);
			if (contentType == null && headers != null && !headers.containsKey("Content-Type")) {
				builder.header("Content-Type", "application/json");
			}
			break;
		case "DELETE":
			builder.DELETE();
			break;
		case "PATCH":
			builder.method("PATCH", bodyPublisher);
			if (contentType == null && headers != null && !headers.containsKey("Content-Type")) {
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
