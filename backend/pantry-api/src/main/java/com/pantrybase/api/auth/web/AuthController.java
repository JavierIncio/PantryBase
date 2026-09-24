package com.pantrybase.api.auth.web;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.ChangePasswordRequest;
import com.pantrybase.api.auth.dto.LoginRequest;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.auth.service.AuthService;
import com.pantrybase.api.common.exception.InvalidRefreshTokenException;
import com.pantrybase.api.common.security.CookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for handling authentication-related endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    /**
     * Registers a new user and returns authentication tokens.
     *
     * @param request  the registration request containing user details
     * @param response the HTTP response to add cookies to
     * @return a ResponseEntity containing the authentication tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse tokens = authService.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
    }

    /**
     * Logs in a user and returns authentication tokens.
     *
     * @param request  the login request containing user credentials
     * @param response the HTTP response to add cookies to
     * @return a ResponseEntity containing the authentication tokens
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {

        AuthResponse tokens = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.ok(tokens);
    }

    /**
     * Refreshes the authentication tokens using the provided refresh token.
     *
     * @param rtCookie the refresh token cookie
     * @param response the HTTP response to add cookies to
     * @return a ResponseEntity containing the new authentication tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(value = "refresh_token", required = false) String rtCookie,
                                                HttpServletResponse response) {
        if (rtCookie == null) throw new InvalidRefreshTokenException();
        AuthResponse tokens = authService.refreshTokens(rtCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.ok(tokens);
    }

    /**
     * Logs out a user by invalidating the refresh token and clearing the cookie.
     *
     * @param rtCookie the refresh token cookie
     * @param response the HTTP response to clear cookies
     * @return a ResponseEntity with no content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false) String rtCookie,
                                       HttpServletResponse response) {
        if (rtCookie != null) authService.logout(rtCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
