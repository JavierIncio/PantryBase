package com.pantrybase.api.recipe.dto;

import com.pantrybase.api.catalog.domain.UnitCategory;
import java.math.BigDecimal;

/**
 * One ingredient line of a recipe, as shown to the client.
 *
 * <p>Both the original (provider) and canonical (pantry) quantities travel together
 * so the UI can show "2 cups" and also compute coverage against the pantry's grams.</p>
 */
public record RecipeIngredientDto(
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