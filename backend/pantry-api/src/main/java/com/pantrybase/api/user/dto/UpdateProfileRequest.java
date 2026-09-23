package com.pantrybase.api.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Complete replacement of the user's profile information; email is not included in
 * this request, as it is immutable.
 *
 * <ul>
 *     <li>username:  ignored if null</li>
 *     <li>firstName: cleared if null</li>
 *     <li>lastName:  cleared if null</li>
 * </ul>
 */
public record UpdateProfileRequest(
        @Size(min = 3, max = 20) String username,
        @Size(max=50) String firstName,
        @Size(max=50) String lastName
) {}
