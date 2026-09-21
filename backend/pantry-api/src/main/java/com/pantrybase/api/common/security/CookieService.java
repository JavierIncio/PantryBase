package com.pantrybase.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Service class for managing HTTP cookies related to authentication.
 */
@Component
public class CookieService {

    private final JwtService jwtService;
    private final boolean cookieSecure;

    public CookieService(JwtService jwtService,
                         @Value("${app.security.cookie.secure}") boolean cookieSecure) {
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    /**
     * Creates a new HTTP cookie for the refresh token.
     *
     * <ul>
     *     <li>The cookie is named "refresh_token".</li>
     *     <li>The cookie is marked as HttpOnly to prevent access from client-side scripts.</li>
     *     <li>The cookie's secure attribute is set based on the configuration.</li>
     *     <li>The cookie's SameSite attribute is set to "Lax" to provide some protection against CSRF attacks.</li>
     *     <li>The cookie's path is set to "/api/auth" to restrict its scope to authentication-related endpoints.</li>
     *     <li>The cookie's max age is set based on the refresh token's time-to-live (TTL) as provided by the JwtService.</li>
     * </ul>
     *
     * @param refreshToken the refresh token to be stored in the cookie
     * @return a ResponseCookie configured with the specified refresh token and security settings
     */
    public ResponseCookie create(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtService.getRefreshTtl())
                .build();
    }

    /**
     * Clears the refresh token cookie by setting its value to an empty string and its max age to zero.
     *
     * @return a ResponseCookie configured to clear the refresh token cookie
     */
    public ResponseCookie clear() {
        return ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
