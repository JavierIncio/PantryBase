package com.pantrybase.api.auth.dto;

/**
 * Represents the response returned after a successful authentication request.
 *
 * @param accessToken  The access token issued to the client.
 * @param refreshToken The refresh token issued to the client.
 * @param tokenType    The type of the token (e.g., "Bearer").
 * @param expiresIn    The duration in seconds until the access token expires.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {}
