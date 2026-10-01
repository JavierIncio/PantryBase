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
 *       own household measures over the category density.</li>
 *   <li>ingredientCategory is optional and used as fallback density for weight-volume
 *       conversions when the ingredient has no measure for the volume unit.</li>
 * </ul>
 */
public record ConvertUnitsRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String from,
        @NotBlank String to,
        Long ingredientId,
        String ingredientCategory
) {}