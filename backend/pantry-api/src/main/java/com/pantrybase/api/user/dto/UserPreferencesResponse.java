package com.pantrybase.api.user.dto;

import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.domain.FilterMode;

/**
 * Representation of the user's preferences regarding filtering and dietary choices.
 */
public record UserPreferencesResponse(
        FilterMode filterMode,
        Short coverageThreshold,
        Diet diet
) {}
