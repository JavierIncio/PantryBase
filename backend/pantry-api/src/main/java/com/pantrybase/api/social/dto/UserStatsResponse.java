package com.pantrybase.api.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Aggregated cooking statistics for a user's profile.
 */
@Schema(name = "UserStatsResponse", description = "Cooking statistics shown on the user profile")
public record UserStatsResponse(
        @Schema(description = "Number of completed cooking sessions (times cooked)", example = "12")
        long cookedSessions,
        @Schema(description = "Number of distinct recipes cooked at least once", example = "7")
        long distinctRecipes,
        @Schema(description = "Most used ingredients, most used first")
        List<IngredientUsageResponse> topIngredients
) {}
