package com.ajay.resilient;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * An {@link HttpClient} wrapper with resilience baked in:
 *
 * <ol>
 *   <li><b>Circuit breaker</b> — fails fast when the downstream is known-bad.</li>
 *   <li><b>Timeouts</b> — connect timeout on the client, per-request timeout on the call.</li>
 *   <li><b>Retries</b> — exponential backoff with jitter, only for retryable failures
 *       (I/O errors, 429, 5xx — never 4xx).</li>
 * </ol>
 *
 * <p>One instance per downstream service, so each gets its own breaker.
 */
public final class ResilientHttpClient {

    private final Transport transport;
    private final RetryPolicy retryPolicy;
    private final CircuitBreaker breaker;
    private final Duration requestTimeout;

    /** Production constructor: real HTTP transport. */
    public ResilientHttpClient(RetryPolicy retryPolicy,
                               CircuitBreaker breaker,
                               Duration connectTimeout,
                               Duration requestTimeout) {
        this(retryPolicy, breaker, new HttpClientTransport(connectTimeout), requestTimeout);
    }

    /** Test/demo constructor: inject any transport (e.g. a scripted fake). */
    public ResilientHttpClient(RetryPolicy retryPolicy,
                               CircuitBreaker breaker,
                               Transport transport,
                               Duration requestTimeout) {
        this.transport = transport;
        this.retryPolicy = retryPolicy;
        this.breaker = breaker;
        this.requestTimeout = requestTimeout;
    }

    /** Sends the request, applying breaker → timeout → retry. */
    public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        if (!breaker.allowRequest()) {
            throw new CircuitOpenException(
                    "circuit is OPEN for " + request.uri() + " — failing fast");
        }

        HttpRequest timed = HttpRequest.newBuilder(request, (k, v) -> true)
                .timeout(requestTimeout)
                .build();

        int attempt = 0;
        while (true) {
            attempt++;
            try {
                HttpResponse<String> response = transport.exchange(timed);
                int status = response.statusCode();
                if (retryPolicy.shouldRetry(attempt, null, status)) {
                    sleepBeforeRetry(attempt, null, status);
                    continue;
                }
                if (status < 500) {
                    breaker.recordSuccess();
                } else {
                    breaker.recordFailure();
                }
                return response;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (retryPolicy.shouldRetry(attempt, e, -1)) {
                    sleepBeforeRetry(attempt, e, -1);
                    continue;
                }
                breaker.recordFailure();
                if (e instanceof IOException ioe) throw ioe;
                throw (InterruptedException) e;
            }
        }
    }

    public CircuitBreaker.State breakerState() {
        return breaker.getState();
    }

    private void sleepBeforeRetry(int attempt, Throwable error, int status) throws InterruptedException {
        Duration delay = retryPolicy.delayBefore(attempt + 1);
        String cause = error != null ? error.getClass().getSimpleName() : "HTTP " + status;
        System.out.printf("  attempt %d failed (%s), retrying in %d ms [breaker=%s]%n",
                attempt, cause, delay.toMillis(), breaker.getState());
        Thread.sleep(delay.toMillis());
    }
}
