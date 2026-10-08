package com.pantrybase.api.recipe.dto;

import java.math.BigDecimal;

/** Coverage of one ingredient: how much the pantry has vs what the recipe needs. */
public record IngredientCoverageDto(
        long ingredientId,
        String ingredientName,
        BigDecimal neededAmount,
        String neededUnitCode,
        BigDecimal availableAmount,
        String availableUnitCode,
        boolean fullyCovered
) {}