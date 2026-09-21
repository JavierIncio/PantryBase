package com.pantrybase.api.common.exception;

/**
 * Exception thrown when a provided refresh token is invalid or cannot be used to obtain a new access token.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    /**
     * Constructs a new InvalidRefreshTokenException with a default message.
     */
    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
