package com.pantrybase.api.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request to create a user recipe. */
public record CreateRecipeRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String category,
        @Size(max = 100) String area,
        String instructions,
        @Size(max = 512) String imageUrl,
        @Size(max = 512) String videoUrl,
        List<@Size(max = 64) String> tags,
        Integer servings,
        @Size(max = 512) String sourceUrl,
        @NotNull List<@Valid CreateRecipeIngredientRequest> ingredients
) {}