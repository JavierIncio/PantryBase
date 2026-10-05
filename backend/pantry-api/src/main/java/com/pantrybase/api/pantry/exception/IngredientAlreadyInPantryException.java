package com.pantrybase.api.pantry.exception;

/**
 * Signals that the ingredient is already in the user's pantry.
 *
 * <p>An add is not a merge: quietly folding a second bottle into the existing quantity
 * would make the stored number disagree with what the user just told us, and would leave
 * no trace that two separate additions happened. The caller is told to use the
 * adjustment instead, which records the movement.</p>
 */
public class IngredientAlreadyInPantryException extends RuntimeException {

    public IngredientAlreadyInPantryException(long fdcId) {
        super("Ingredient " + fdcId + " is already in the pantry; use the adjustment endpoint to change its quantity");
    }
}