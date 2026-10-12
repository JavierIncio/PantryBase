package com.pantrybase.api.cooking.dto;

import java.math.BigDecimal;

/**
 * One recipe ingredient, as a deduction on completion.
 *
 * <p>Carries the numbers both the plan before the deduction and the result after it
 * need, so the same record expresses a charged 409 and the summary of a completed
 * session. In the 409 the values are the projection of accepting — {@code deducted}
 * is what would be taken ({@code min(available, needed)}) and {@code remaining} what
 * the line would be left at — because the client must show the user the consequence
 * before they choose. {@code available} is what the pantry held at completion time,
 * zero for a line the user never had.</p>
 */
public record DeductionLineResponse(
        long ingredientId,
        String ingredientName,
        String unitCode,
        BigDecimal needed,
        BigDecimal available,
        BigDecimal deducted,
        BigDecimal remaining
) {}