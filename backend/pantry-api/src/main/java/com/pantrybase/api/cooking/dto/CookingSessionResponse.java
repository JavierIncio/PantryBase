package com.pantrybase.api.cooking.dto;

import com.pantrybase.api.cooking.domain.CookingSessionStatus;

import java.time.Instant;

/**
 * A cooking session as its owner sees it.
 *
 * <p>{@code deduction} is only present when this response answers a completion: the
 * per-ingredient what was deducted, so the caller can show the user the state their
 * pantry just moved to. Carried here (nullable) rather than a second response type so
 * the state endpoint keeps a single shape to parse.</p>
 */
public record CookingSessionResponse(
        long id,
        long recipeId,
        CookingSessionStatus status,
        Instant startedAt,
        Instant completedAt,
        CompletionDeductionResponse deduction
) {}