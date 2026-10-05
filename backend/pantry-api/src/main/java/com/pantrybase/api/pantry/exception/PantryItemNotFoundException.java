package com.pantrybase.api.pantry.exception;

/**
 * Signals that a pantry item does not exist for the requesting user.
 *
 * <p>Also raised when the id belongs to another user, which maps to the same 404 on
 * purpose: a 403 would confirm that the id is real, letting a caller enumerate other
 * people's inventories by watching which ids are refused rather than merely absent.</p>
 */
public class PantryItemNotFoundException extends RuntimeException {

    public PantryItemNotFoundException(long id) {
        super("Pantry item " + id + " not found");
    }
}