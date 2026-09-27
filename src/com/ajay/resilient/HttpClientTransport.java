package com.ajay.resilient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** {@link Transport} backed by a real {@link HttpClient}. */
public final class HttpClientTransport implements Transport {

    private final HttpClient http;

    public HttpClientTransport(Duration connectTimeout) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    @Override
    public HttpResponse<String> exchange(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
