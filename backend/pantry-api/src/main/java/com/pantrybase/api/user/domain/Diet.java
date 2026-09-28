package com.pantrybase.api.user.domain;

/**
 * Enum representing different dietary preferences or restrictions.
 * Provisional until the diet-label mapping is validated against the
 * external recipe provider (TheMealDB does not expose diet labels).
 */
public enum Diet {
    BALANCED,
    HIGH_FIBER,
    HIGH_PROTEIN,
    LOW_CARB,
    LOW_FAT,
    LOW_SODIUM
}
