package com.pantrybase.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String loginMethod, // can be either username or email
        @NotBlank String password
) {}
