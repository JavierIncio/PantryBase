package com.pantrybase.api.recipe.dto;

import com.pantrybase.api.catalog.domain.UnitCategory;
import java.math.BigDecimal;

/** One ingredient line with full detail (no coverage). */
public record RecipeIngredientDetailDto(
        long ingredientId,
        String ingredientName,
        String ingredientCategory,
        BigDecimal originalAmount,
        String originalUnitCode,
        BigDecimal canonicalAmount,
        String canonicalUnitCode,
        UnitCategory canonicalUnitCategory,
        int position
) {}