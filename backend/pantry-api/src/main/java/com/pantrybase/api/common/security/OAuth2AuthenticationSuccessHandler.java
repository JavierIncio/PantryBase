package com.pantrybase.api.common.security;

import com.pantrybase.api.auth.dto.AuthResponse;
import com.pantrybase.api.auth.service.AuthService;
import com.pantrybase.api.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final CookieService cookieService;

    @Value("${app.security.oauth2.redirect-uri}")
    private String frontendRedirectUrl;

    public OAuth2AuthenticationSuccessHandler(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oAuth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oAuth2Token.getPrincipal();

        User user = authService.linkOrCreateOAuthUser(oAuth2User.getAttributes());
        AuthResponse tokens = authService.issueTokens(user);

        response.addHeader(HttpHeaders.SET_COOKIE, cookieService.create(tokens.refreshToken()).toString());
        response.sendRedirect(frontendRedirectUrl + "#accessToken=" + tokens.accessToken());
    }
}
