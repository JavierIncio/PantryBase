package com.pantrybase.api.catalog.exception;

public class IngredientCategoryRequiredException extends RuntimeException {
    public IngredientCategoryRequiredException(String from, String to) {
        super(String.format("ingredientCategory is required to convert from %s to %s.", from, to));
    }
}
