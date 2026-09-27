package com.ajay.resilient;

import java.time.Duration;

/**
 * Classic circuit breaker: CLOSED → OPEN → HALF_OPEN → CLOSED.
 *
 * <ul>
 *   <li><b>CLOSED</b> — traffic flows; {@code failureThreshold} consecutive failures trip it.</li>
 *   <li><b>OPEN</b> — calls fail fast with {@link CircuitOpenException}; no threads pile
 *       up waiting on a dead downstream. After {@code openTimeout}, one probe is let through.</li>
 *   <li><b>HALF_OPEN</b> — the probe: success closes the breaker, failure re-opens it.</li>
 * </ul>
 *
 * <p>Counts are consecutive (not windowed): a single success resets the failure count.
 * Good enough for client-side protection; windowed counting (e.g. "50% of last 20")
 * is the next step if you need it.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openTimeout;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtNanos;

    public CircuitBreaker(int failureThreshold, Duration openTimeout) {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold >= 1");
        this.failureThreshold = failureThreshold;
        this.openTimeout = openTimeout;
    }

    /** True if the caller may proceed. Moves OPEN → HALF_OPEN once the timeout elapses. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.nanoTime() - openedAtNanos >= openTimeout.toNanos()) {
                state = State.HALF_OPEN;
                return true; // the single probe
            }
            return false;
        }
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        state = State.CLOSED;
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            trip(); // probe failed: straight back to OPEN
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            trip();
        }
    }

    public synchronized State getState() {
        // Report HALF_OPEN promptly once the timeout has elapsed, even before
        // the next allowRequest() call.
        if (state == State.OPEN
                && System.nanoTime() - openedAtNanos >= openTimeout.toNanos()) {
            state = State.HALF_OPEN;
        }
        return state;
    }

    private void trip() {
        state = State.OPEN;
        openedAtNanos = System.nanoTime();
        consecutiveFailures = 0;
    }
}
