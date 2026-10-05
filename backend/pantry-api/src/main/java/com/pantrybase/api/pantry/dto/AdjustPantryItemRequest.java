package com.pantrybase.api.pantry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A signed movement of stock on an existing pantry line.
 *
 * <p>{@code amount} is signed rather than carrying a direction or a boolean: a quantity
 * has a sign, and one field with a sign cannot be read backwards. It also cannot be
 * zero, which is checked in the service because bean validation has no way to express
 * "neither positive nor negative".</p>
 *
 * <p>{@code unitCode} is free, as on creation, and the amount is converted into the
 * unit the line already stores. The line's unit is immutable, so a caller that had to
 * know it would have to ask for it before it could send anything at all.</p>
 */
public record AdjustPantryItemRequest(
        @NotNull BigDecimal amount,
        @NotBlank @Size(max = 16) String unitCode
) {}