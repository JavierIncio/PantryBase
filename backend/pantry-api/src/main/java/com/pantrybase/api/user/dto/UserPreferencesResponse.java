package com.pantrybase.api.user.dto;

import com.pantrybase.api.user.domain.Diet;
import com.pantrybase.api.user.domain.FilterMode;
import com.pantrybase.api.user.domain.ThemeId;

/**
 * Representation of the user's preferences regarding filtering, dietary choices and theme.
 */
public record UserPreferencesResponse(
        FilterMode filterMode,
        Short coverageThreshold,
        Diet diet,
        ThemeId themeId,
        Boolean useAi
) {}
