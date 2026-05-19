package com.greengrub.usermanagement.exception;

/**
 * Wraps transient database failures (connection issues, timeouts, deadlocks).
 * Resilience4j retry/circuit breaker is configured to match on this exception.
 */
public class UserStorageException extends RuntimeException {

    public UserStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
