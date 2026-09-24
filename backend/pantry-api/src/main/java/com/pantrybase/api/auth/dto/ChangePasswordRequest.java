package com.pantrybase.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A record for the request to change a user's password.
 *
 * <p>currentPassword can be blank if the user is authenticated via OAuth.</p>
 *
 * @param currentPassword The user's current password.
 * @param newPassword The user's new password.
 */
public record ChangePasswordRequest(
        @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
