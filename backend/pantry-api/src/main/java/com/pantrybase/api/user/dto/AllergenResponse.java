package com.pantrybase.api.user.dto;

/**
 * Full representation of an allergen.
 */
public record AllergenResponse(
        Short id,
        String code,
        String name
) {}
