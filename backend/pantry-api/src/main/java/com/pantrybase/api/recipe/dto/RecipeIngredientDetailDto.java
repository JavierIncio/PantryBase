package com.pantrybase.api.recipe.dto;

import com.pantrybase.api.catalog.domain.UnitCategory;
import java.math.BigDecimal;

/**
 * One ingredient line with full detail (no coverage).
 *
 * <p>{@code ingredientId} is null and {@code matched} false for provider lines that did
 * not map to a catalog row: the name shown is then the provider's own wording, the
 * quantities are null and the line can never be covered.</p>
 */
public record RecipeIngredientDetailDto(
        Long ingredientId,
        String ingredientName,
        String ingredientCategory,
        BigDecimal originalAmount,
        String originalUnitCode,
        BigDecimal canonicalAmount,
        String canonicalUnitCode,
        UnitCategory canonicalUnitCategory,
        boolean matched,
        int position
) {}