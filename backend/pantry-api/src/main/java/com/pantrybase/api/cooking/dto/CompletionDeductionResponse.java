package com.pantrybase.api.cooking.dto;

import java.util.List;

/**
 * What completing a session did to the pantry, line by line.
 *
 * <p>{@code hasShortage} is true when at least one line was not fully covered and the
 * session was completed anyway, so a caller that accepted the shortfall can still flag
 * it, and a caller that never wanted one can detect the edge case instead of comparing
 * {@code deducted} to {@code needed} itself.</p>
 */
public record CompletionDeductionResponse(
        boolean hasShortage,
        List<DeductionLineResponse> lines
) {}