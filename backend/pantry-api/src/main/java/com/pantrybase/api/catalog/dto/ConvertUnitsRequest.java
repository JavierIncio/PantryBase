package com.pantrybase.api.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Represents a request to convert units of measurement.
 *
 * <ul>
 *   <li>The amount must be a positive or zero value (≥ 0).</li>
 *   <li>ingredientId is optional; when given, the conversion prefers the ingredient's
 *       own household measures, then the density class curated for it.</li>
 *   <li>densityClass is optional and used as fallback for weight-volume conversions when
 *       the ingredient has no measure and no class of its own. It is one of our own class
 *       codes (for instance {@code FLOUR}), not the provider's category: USDA FDC
 *       categories are top level and mix densities that differ by more than 2x, so
 *       resolving a density from them would return a plausible but wrong weight.</li>
 * </ul>
 */
public record ConvertUnitsRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String from,
        @NotBlank String to,
        Long ingredientId,
        String densityClass
) {}