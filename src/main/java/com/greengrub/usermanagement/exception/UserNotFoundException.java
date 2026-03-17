package com.greengrub.usermanagement.exception;

/**
 * Exception thrown when a user is not found
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found with id: " + userId);
    }

    public UserNotFoundException(String field, String value) {
        super(String.format("User not found with %s: %s", field, value));
    }
}
