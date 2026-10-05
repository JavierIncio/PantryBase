package com.pantrybase.api.pantry.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Adds an ingredient to the pantry.
 *
 * <p>The amount is free in any unit the catalog knows; it is normalized to the unit the
 * pantry stores before being persisted, so the caller does not have to know which one
 * that is.</p>
 *
 * <p>{@code fdcId} rather than the catalog's internal id: the client only ever sees
 * provider ids in the catalog, and accepting an internal id here would tie the endpoint
 * to a numbering it has no reason to know.</p>
 */
public record CreatePantryItemRequest(
        @NotNull @Positive Long fdcId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotBlank String unitCode,
        @Size(max = 100) String location,
        @Size(max = 255) String note
) {}