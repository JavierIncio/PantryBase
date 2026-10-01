package com.pantrybase.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Assigns a curated density class to an ingredient.
 *
 * <p>The class must be one of the existing {@code density_classes}; an unknown code is
 * rejected rather than created, so this endpoint cannot become a way to introduce an
 * unsourced density.</p>
 */
public record AssignDensityClassRequest(
        @NotBlank String densityClass
) {}
