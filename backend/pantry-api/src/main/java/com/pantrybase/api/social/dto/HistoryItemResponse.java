package com.pantrybase.api.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Entry of the cooking history for a user.
 */
@Schema(name = "HistoryItemResponse", description = "Entry of the cooking history")
public record HistoryItemResponse(
        @Schema(description = "History entry id", example = "1") Long id,
        @Schema(description = "User id", example = "10") Long userId,
        @Schema(description = "Recipe id", example = "5") Long recipeId,
        @Schema(description = "Cooking session id that produced this entry", example = "3") Long cookingSessionId,
        @Schema(description = "When it was recorded") Instant createdAt
) {}
