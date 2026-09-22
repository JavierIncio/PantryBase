package com.pantrybase.api.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Complete replacement state; an empty list clears all exclusions.
 */
public record ReplaceAllergyExclusionsRequest(
        @NotNull List<String> codes
) {}
