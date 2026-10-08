package com.pantrybase.api.recipe.exception;

/**
 * Signals that a recipe does not exist.
 *
 * <p>Recipes are not owned by a user (coverage is computed on read, not stored), so a
 * missing id and a malformed id are the same 404 and there is no cross-user case to
 * hide.</p>
 */
public class RecipeNotFoundException extends RuntimeException {

    public RecipeNotFoundException(long id) {
        super("Recipe " + id + " not found");
    }
}
