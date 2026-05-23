package com.greengrub.usermanagement.exception;

/**
 * Wraps a transient failure when calling donation-service over gRPC. Triggers
 * the donationServiceRetry / donationServiceBreaker policies and is mapped to
 * a 503 by GlobalExceptionHandler so callers know to retry rather than treat
 * it as a client error.
 */
public class DonationServiceException extends RuntimeException {

    public DonationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
