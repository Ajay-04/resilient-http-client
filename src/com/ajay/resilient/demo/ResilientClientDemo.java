package com.ajay.resilient.demo;

import com.ajay.resilient.CircuitBreaker;
import com.ajay.resilient.CircuitOpenException;
import com.ajay.resilient.ResilientHttpClient;
import com.ajay.resilient.RetryPolicy;
import com.ajay.resilient.Transport;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Drives the {@link ResilientHttpClient} against a scripted transport —
 * no sockets needed, so the demo runs anywhere:
 *
 * <ol>
 *   <li><b>/flaky</b> — returns 500 twice, then 200. Watch the retries land it.</li>
 *   <li><b>/down</b> — always 500. Watch the breaker trip and fail fast, then
 *       report half-open after the timeout.</li>
 * </ol>
 */
public class ResilientClientDemo {

    public static void main(String[] args) throws Exception {
        ScriptedTransport transport = new ScriptedTransport(
                new ScriptedTransport.Outcome.Status(500, "boom")); // default: dead
        transport.script("/flaky",
                new ScriptedTransport.Outcome.Status(500, "boom"),
                new ScriptedTransport.Outcome.Status(500, "boom"),
                new ScriptedTransport.Outcome.Status(200, "ok"));

        scenarioFlakyThenOk(transport);
        scenarioBreakerTrips(transport);
    }

    private static ResilientHttpClient newClient(Transport transport) {
        return new ResilientHttpClient(
                RetryPolicy.builder()
                        .maxAttempts(4)
                        .initialDelay(Duration.ofMillis(100))
                        .multiplier(2.0)
                        .maxDelay(Duration.ofSeconds(2))
                        .jitter(false) // deterministic demo output; jitter on in prod
                        .build(),
                new CircuitBreaker(3, Duration.ofSeconds(2)),
                transport,
                Duration.ofSeconds(5)); // request timeout
    }

    private static void scenarioFlakyThenOk(ScriptedTransport transport) throws Exception {
        System.out.println("== Scenario 1: flaky endpoint (500, 500, 200) ==");
        ResilientHttpClient client = newClient(transport);
        HttpResponse<String> r = client.send(
                HttpRequest.newBuilder(URI.create("http://service.local/flaky")).GET().build());
        System.out.printf("  final: HTTP %d body=%s [breaker=%s]%n",
                r.statusCode(), r.body(), client.breakerState());
    }

    private static void scenarioBreakerTrips(ScriptedTransport transport) throws Exception {
        System.out.println("== Scenario 2: dead endpoint, breaker trips ==");
        ResilientHttpClient client = newClient(transport);
        URI down = URI.create("http://service.local/down");

        // Three send() calls, each ending in a terminal 500 = 3 breaker failures -> OPEN.
        for (int i = 1; i <= 3; i++) {
            try {
                HttpResponse<String> r = client.send(
                        HttpRequest.newBuilder(down).GET().build());
                System.out.printf("  call %d: HTTP %d [breaker=%s]%n",
                        i, r.statusCode(), client.breakerState());
            } catch (CircuitOpenException e) {
                System.out.printf("  call %d: %s%n", i, e.getMessage());
            }
        }
        // Breaker is OPEN now: the next call fails fast without any I/O.
        long start = System.currentTimeMillis();
        try {
            client.send(HttpRequest.newBuilder(down).GET().build());
        } catch (CircuitOpenException e) {
            System.out.printf("  fast-fail took %d ms: %s [breaker=%s]%n",
                    System.currentTimeMillis() - start, e.getMessage(), client.breakerState());
        }
        // After the open timeout, the breaker reports half-open (probe allowed).
        System.out.println("  waiting out the 2s open timeout...");
        Thread.sleep(2100);
        System.out.println("  breaker now reports: " + client.breakerState());
    }
}
