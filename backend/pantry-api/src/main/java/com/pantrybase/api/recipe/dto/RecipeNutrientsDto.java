package com.pantrybase.api.recipe.dto;

import java.math.BigDecimal;

/** Per-serving nutrient profile. */
public record RecipeNutrientsDto(
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbsG
) {}