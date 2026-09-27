package com.ajay.resilient.demo;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

/** Minimal in-memory {@link HttpResponse} for scripted transports. */
final class SimpleHttpResponse implements HttpResponse<String> {

    private final int status;
    private final String body;
    private final HttpRequest request;

    SimpleHttpResponse(int status, String body, HttpRequest request) {
        this.status = status;
        this.body = body;
        this.request = request;
    }

    @Override
    public int statusCode() {
        return status;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return HttpHeaders.of(Map.of(), (k, v) -> true);
    }

    @Override
    public String body() {
        return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public java.net.URI uri() {
        return request.uri();
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
