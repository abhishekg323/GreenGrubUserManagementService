package com.greengrub.usermanagement.exception;

/**
 * Exception thrown when a user already exists
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email);
    }
}
