package com.pantrybase.api.common.exception;

/**
 * Exception thrown when a user is not found in the system.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String loginMethod) {
        super(String.format("User '%s' not found", loginMethod));
    }
}
