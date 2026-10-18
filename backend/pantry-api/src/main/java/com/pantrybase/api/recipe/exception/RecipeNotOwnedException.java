package com.pantrybase.api.recipe.exception;

/**
 * Signals an attempt to publish or unpublish a recipe the caller does not own.
 *
 * <p>Unlike a missing recipe (which is a 404 because there is nothing to disclose), a
 * recipe that exists but belongs to somebody else is a 403: the caller can see it, they
 * just may not change its visibility.</p>
 */
public class RecipeNotOwnedException extends RuntimeException {

    public RecipeNotOwnedException(long id) {
        super("Recipe " + id + " belongs to another user");
    }
}
