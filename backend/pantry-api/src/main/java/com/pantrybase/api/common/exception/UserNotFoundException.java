package com.pantrybase.api.common.exception;

/**
 * Exception thrown when a user is not found in the system.
 */
public class UserNotFoundException extends RuntimeException {
    /**
     * Constructs a new UserNotFoundException with a detailed message.
     *
     * @param loginMethod The login method used to search for the user.
     */
    public UserNotFoundException(String loginMethod) {
        super(String.format("User '%s' not found", loginMethod));
    }
}
