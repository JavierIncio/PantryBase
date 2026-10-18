package com.pantrybase.api.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether the current user has a recipe marked as favorite and/or saved.
 */
@Schema(name = "RecipeSocialFlags", description = "Favorite/saved flags of the current user for one recipe")
public record RecipeSocialFlags(
        @Schema(description = "Is the recipe a favorite of the current user", example = "true") boolean favorite,
        @Schema(description = "Did the current user save the recipe for later", example = "false") boolean saved
) {}
