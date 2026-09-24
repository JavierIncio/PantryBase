package com.pantrybase.api.common.exception;

/**
 * Exception thrown when the provided current password does not match the user's actual current password.
 */
public class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() {
        super("Current password does not match.");
    }
}
