package com.pantrybase.api.pantry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A consumption of an existing pantry line, without a cooking session.
 *
 * <p>Positive-only on purpose, unlike {@link AdjustPantryItemRequest}'s signed amount:
 * the whole point of this endpoint is that stock leaves the shelf, and a sign that could
 * turn it into an add would make it a second, weaker adjustment. {@code unitCode} is
 * free and the amount is converted into the unit the line already stores, as everywhere
 * else in the pantry.</p>
 */
public record ConsumePantryItemRequest(
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(max = 16) String unitCode
) {}