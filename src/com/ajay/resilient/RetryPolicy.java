package com.ajay.resilient;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines when and how long to wait between attempts.
 *
 * <p>Delay for attempt {@code n} (1-based) is
 * {@code min(maxDelay, initialDelay * multiplier^(n-1))}, with "full jitter"
 * applied by default: the actual sleep is uniform in {@code [0, computed]}.
 * Jitter matters — without it, every client retries in lockstep and the
 * recovering server gets hit by a synchronized wave (the thundering herd).
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;
    private final boolean jitter;

    private RetryPolicy(Builder b) {
        this.maxAttempts = b.maxAttempts;
        this.initialDelay = b.initialDelay;
        this.multiplier = b.multiplier;
        this.maxDelay = b.maxDelay;
        this.jitter = b.jitter;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    /** Delay before attempt {@code attempt} (1-based; attempt 1 has no delay). */
    public Duration delayBefore(int attempt) {
        if (attempt <= 1) {
            return Duration.ZERO;
        }
        double exp = initialDelay.toNanos() * Math.pow(multiplier, attempt - 2);
        long capped = Math.min((long) exp, maxDelay.toNanos());
        long nanos = jitter
                ? ThreadLocalRandom.current().nextLong(capped + 1)
                : capped;
        return Duration.ofNanos(nanos);
    }

    /**
     * Whether a failed attempt deserves another try. Retries I/O failures and
     * timeouts always; retries HTTP 429 and 5xx (except 501, which won't fix
     * itself); never retries 4xx — the request itself is wrong.
     */
    public boolean shouldRetry(int attempt, Throwable error, int httpStatus) {
        if (attempt >= maxAttempts) {
            return false;
        }
        if (error != null) {
            return true; // connect timeout, reset, DNS...: worth another shot
        }
        return httpStatus == 429 || (httpStatus >= 500 && httpStatus != 501);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private Duration initialDelay = Duration.ofMillis(200);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(10);
        private boolean jitter = true;

        public Builder maxAttempts(int n) { this.maxAttempts = n; return this; }
        public Builder initialDelay(Duration d) { this.initialDelay = d; return this; }
        public Builder multiplier(double m) { this.multiplier = m; return this; }
        public Builder maxDelay(Duration d) { this.maxDelay = d; return this; }
        public Builder jitter(boolean j) { this.jitter = j; return this; }

        public RetryPolicy build() {
            if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts >= 1");
            return new RetryPolicy(this);
        }
    }
}
