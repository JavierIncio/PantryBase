package com.pantrybase.api.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieService {

    private final JwtService jwtService;
    private final boolean cookieSecure;

    public CookieService(JwtService jwtService,
                         @Value("${app.security.cookie.secure}") boolean cookieSecure) {
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
    }

    public ResponseCookie create(String refreshToken) {
        return ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtService.getRefreshTtl())
                .build();
    }

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
