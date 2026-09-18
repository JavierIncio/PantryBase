package com.pantrybase.api.common.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String loginMethod) {
        super(String.format("User '%s' not found", loginMethod));
    }
}
