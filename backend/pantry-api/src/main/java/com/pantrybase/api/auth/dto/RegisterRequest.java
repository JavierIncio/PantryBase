package com.pantrybase.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 20) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName
) {}
