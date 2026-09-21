package com.pantrybase.api.common.exception;

/**
 * Exception thrown when attempting to register a user with a username that already exists in the system.
 */
public class UsernameAlreadyExistsException extends RuntimeException {
    /**
     * Constructs a new UsernameAlreadyExistsException with a detailed message.
     *
     * @param username The username that already exists.
     */
    public UsernameAlreadyExistsException(String username) {
        super(String.format("User with username '%s' already exists", username));
    }
}
