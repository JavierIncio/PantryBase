package com.pantrybase.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetTokenRequest(@NotBlank String loginMethod) {}
