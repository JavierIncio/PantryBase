package com.pantrybase.api.pantry.dto;

import com.pantrybase.api.pantry.domain.ConsumptionOrigin;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One movement of stock, as the history of a line needs it.
 *
 * <p>{@code deltaQuantity} is signed and is the same number the row stores, not a
 * recomputed one: reading a history that disagreed with the ledger would make it
 * useless for the only question it exists to answer.</p>
 *
 * <p>Deliberately no quantity-after field. It is the running sum of the deltas above it,
 * so publishing it per entry would state the same fact twice and give a future
 * divergence somewhere to hide.</p>
 */
public record ConsumptionEntryResponse(
        long id,
        BigDecimal deltaQuantity,
        ConsumptionOrigin origin,
        Instant createdAt
) {}