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

/**
 * Handles successful OAuth2 authentication by linking or creating a user, issuing tokens, and redirecting to the frontend.
 */
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

    /**
     * Called when OAuth2 authentication is successful.
     *
     * <p>Links or creates a user, issues access and refresh tokens, stores the
     * refresh token in a cookie, and redirects the browser to the frontend with
     * the access token in the URL fragment.</p>
     *
     * <p>The URL fragment is handled by the browser and is not included in the
     * HTTP request sent to the frontend server.</p>
     *
     * @param request        the HttpServletRequest
     * @param response       the HttpServletResponse
     * @param authentication the Authentication object containing the authenticated OAuth2 user
     * @throws IOException if an input or output exception occurs
     */
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
