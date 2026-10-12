package com.pantrybase.api.cooking.exception;

import com.pantrybase.api.cooking.domain.CookingSessionStatus;

/**
 * A state change the session cannot make, either because the move is not in the
 * machine or because the session has already ended.
 *
 * <p>409 rather than 400: the request is well formed, the state it was applied to is
 * what refuses it, mirroring how the pantry reports an adjustment that would leave
 * stock negative.</p>
 */
public class InvalidCookingStateTransitionException extends RuntimeException {

    public InvalidCookingStateTransitionException(CookingSessionStatus from, CookingSessionStatus to) {
        super("Cannot move a cooking session from " + from + " to " + to);
    }
}