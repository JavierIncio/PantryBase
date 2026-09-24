package com.pantrybase.api.common.exception;

/**
 * Exception thrown when a user attempts to perform an action that requires a password, but no password is set.
 */
public class PasswordNotSetException extends RuntimeException {
    public PasswordNotSetException() {
        super("No password is set for this user.");
    }
}
