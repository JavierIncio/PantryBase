package com.pantrybase.api.user.dto;

import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.domain.FilterMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Full representation of the preferences sub-resource; PUT replaces it entirely.
 */
public record UserPreferencesRequest(
        @NotNull FilterMode filterMode,
        @NotNull @Min(0) @Max(100) Short coverageThreshold,
        @NotNull Diet diet
) {}
