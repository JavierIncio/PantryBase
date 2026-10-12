package com.pantrybase.api.cooking.exception;

import com.pantrybase.api.cooking.dto.DeductionLineResponse;

import java.util.List;

/**
 * Completing a session would use more of some ingredient than the pantry holds, and
 * the caller did not accept the shortfall.
 *
 * <p>409 is the status of a valid request refused by the state it was applied to, the
 * same conflict the pantry raises for a negative adjustment. The body is not the usual
 * {@code ErrorResponse} but the deduction plan itself: the caller has to show which
 * lines fall short and what each would become, and it already has the very DTO the
 * frontend renders.</p>
 */
public class CookingShortageException extends RuntimeException {

    private final List<DeductionLineResponse> lines;

    public CookingShortageException(List<DeductionLineResponse> lines) {
        super("Some recipe ingredients exceed the pantry stock");
        this.lines = List.copyOf(lines);
    }

    public List<DeductionLineResponse> getLines() {
        return lines;
    }
}