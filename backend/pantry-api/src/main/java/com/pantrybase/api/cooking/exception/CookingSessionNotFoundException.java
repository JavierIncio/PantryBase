package com.pantrybase.api.cooking.exception;

/**
 * A cooking session the caller reached for does not exist, or is not theirs.
 *
 * <p>One type for both cases, like the pantry's own 404s: the caller cannot know the
 * difference, and telling them which of the two it was would only confirm an id they
 * guessed.</p>
 */
public class CookingSessionNotFoundException extends RuntimeException {

    public CookingSessionNotFoundException(long sessionId) {
        super("Cooking session not found: " + sessionId);
    }
}