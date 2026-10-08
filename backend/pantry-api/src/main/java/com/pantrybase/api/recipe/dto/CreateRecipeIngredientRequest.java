package com.pantrybase.api.recipe.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** One ingredient line in a create request. */
public record CreateRecipeIngredientRequest(
        @NotNull @PositiveOrZero long ingredientId,
        @NotNull BigDecimal amount,
        @NotNull String unitCode,
        int position
) {}