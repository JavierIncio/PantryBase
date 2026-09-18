package com.pantrybase.api.auth.web;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.dto.LoginRequest;
import com.pantrybase.api.auth.dto.RegisterRequest;
import com.pantrybase.api.auth.service.AuthService;
import com.pantrybase.api.common.security.CookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse tokens = authService.register(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {

        AuthResponse tokens = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue("refresh_token") String rtCookie,
                                                HttpServletResponse response) {
        if (rtCookie == null) throw new IllegalArgumentException("Refresh token cookie is missing");
        AuthResponse tokens = authService.refreshTokens(rtCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue("refresh_token") String rtCookie,
                                       HttpServletResponse response) {
        if (rtCookie != null) authService.logout(rtCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.clear().toString());
        return ResponseEntity.noContent().build();
    }
}
