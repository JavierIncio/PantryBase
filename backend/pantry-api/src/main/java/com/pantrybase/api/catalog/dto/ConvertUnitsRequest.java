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
 *   <li>ingredientCategory is optional, as is only used for weight-volume conversions.</li>
 * </ul>
 */
public record ConvertUnitsRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String from,
        @NotBlank String to,
        String ingredientCategory
) {}
