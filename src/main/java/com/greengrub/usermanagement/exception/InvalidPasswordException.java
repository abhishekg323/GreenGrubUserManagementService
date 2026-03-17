package com.greengrub.usermanagement.exception;

/**
 * Exception thrown when password validation fails
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }

    public InvalidPasswordException() {
        super("Invalid password provided");
    }
}
