package com.pantrybase.api.recipe.dto;

/** Query parameters for recipe search. */
public record RecipeSearchRequest(
        String name,
        Long ingredientId,
        Boolean strict,
        Integer threshold,
        Boolean allergenSafe
) {}