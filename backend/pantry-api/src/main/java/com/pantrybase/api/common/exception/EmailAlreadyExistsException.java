package com.pantrybase.api.common.exception;

/**
 * Exception thrown when attempting to register a user with an email that already exists in the system.
 */
public class EmailAlreadyExistsException extends RuntimeException {
    /**
     * Constructs a new EmailAlreadyExistsException with a detailed message.
     *
     * @param email The email address that already exists.
     */
    public EmailAlreadyExistsException(String email) {
        super(String.format("User with email '%s' already exists", email));
    }
}
