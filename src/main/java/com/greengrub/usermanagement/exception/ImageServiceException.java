package com.greengrub.usermanagement.exception;

/**
 * Wraps a transient failure when calling image-service over gRPC. Triggers the
 * imageServiceRetry / imageServiceBreaker policies and is mapped to a 503 by
 * GlobalExceptionHandler so callers know to retry rather than treat it as a
 * client error.
 */
public class ImageServiceException extends RuntimeException {

    public ImageServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
