package com.pantrybase.api.pantry.exception;

/**
 * Signals that the ingredient is not in the local catalog yet.
 *
 * <p>Distinct from the catalog's own not-found because the food may very well exist at
 * the provider: what is missing is the local copy, and the remedy is to read its detail
 * first. That read is also what materializes it, so asking the client to do it keeps
 * the quota policy of the provider in one place instead of spreading provider calls
 * across the pantry endpoints.</p>
 */
public class IngredientNotInCatalogException extends RuntimeException {

    public IngredientNotInCatalogException(long fdcId) {
        super("Ingredient " + fdcId + " is not in the local catalog; read "
                + "/api/catalog/ingredients/" + fdcId + " before adding it to the pantry");
    }
}