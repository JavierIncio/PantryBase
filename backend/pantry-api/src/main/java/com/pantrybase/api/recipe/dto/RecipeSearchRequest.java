package com.pantrybase.api.recipe.dto;

import com.pantrybase.api.user.domain.Diet;

/** Query parameters for recipe search. */
public record RecipeSearchRequest(
        String name,
        Long ingredientId,
        Boolean strict,
        Integer threshold,
        Boolean allergenSafe,
        Diet diet
) {}
