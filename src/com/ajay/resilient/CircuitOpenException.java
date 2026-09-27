package com.ajay.resilient;

/** Thrown (without hitting the network) when the circuit breaker is open. */
public final class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String message) {
        super(message);
    }
}
