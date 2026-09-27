package com.pantrybase.api.catalog.exception;

public class IngredientDensityNotFoundException extends RuntimeException {
    public IngredientDensityNotFoundException(String category) {
        super("No ingredient density established for category: " + category);
    }
}
