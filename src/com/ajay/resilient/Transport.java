package com.ajay.resilient;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The actual network I/O, behind an interface.
 *
 * <p>Production uses {@link HttpClientTransport} (real {@code java.net.http}).
 * Tests and demos use a scripted fake — resilience logic shouldn't need a
 * socket to be verified.
 */
public interface Transport {
    HttpResponse<String> exchange(HttpRequest request) throws IOException, InterruptedException;
}
