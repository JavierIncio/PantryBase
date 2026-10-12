package com.pantrybase.api.cooking.dto;

import com.pantrybase.api.cooking.domain.CookingSessionStatus;
import jakarta.validation.constraints.NotNull;

/**
 * A state transition requested on a session.
 *
 * <p>{@code acceptShortage} lets the caller acknowledge that some lines exceed the
 * pantry before the session is completed. Without it a shortfall is refused and the
 * caller learns of it as a 409 carrying the plan; with it, each short line is deducted
 * down to zero and the difference is taken as already contributed by the user.</p>
 */
public record UpdateCookingSessionStateRequest(
        @NotNull CookingSessionStatus status,
        Boolean acceptShortage
) {}