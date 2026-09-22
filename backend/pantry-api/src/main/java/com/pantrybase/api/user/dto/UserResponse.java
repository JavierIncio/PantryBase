package com.pantrybase.api.user.dto;

import com.pantrybase.api.user.domain.Role;

import java.util.Set;

/**
 * Identity summary of an authenticated user, exposed by the /me endpoint.
 */
public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles
) {}
