package com.pantrybase.api.common.exception;

/**
 * Exception thrown when authentication fails due to invalid credentials.
 */
public class InvalidCredentialsException extends RuntimeException {
    /**
     * Constructs a new InvalidCredentialsException with a default message.
     */
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
