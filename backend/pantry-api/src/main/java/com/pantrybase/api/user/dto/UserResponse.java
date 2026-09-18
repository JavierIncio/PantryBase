package com.pantrybase.api.user.dto;

import com.pantrybase.api.user.domain.Role;

import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles
) {}
