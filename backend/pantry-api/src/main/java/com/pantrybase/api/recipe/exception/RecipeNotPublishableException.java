package com.pantrybase.api.recipe.exception;

/**
 * Signals an attempt to publish or unpublish a recipe that did not come from a user.
 *
 * <p>Only recipes with {@code source = USER} can be published; provider recipes carry
 * no author to publish on behalf of.</p>
 */
public class RecipeNotPublishableException extends RuntimeException {

    public RecipeNotPublishableException(long id) {
        super("Recipe " + id + " is not a user recipe and cannot be published");
    }
}
