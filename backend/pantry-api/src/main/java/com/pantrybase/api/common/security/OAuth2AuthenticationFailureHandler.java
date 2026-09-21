package com.pantrybase.api.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom AuthenticationFailureHandler that handles OAuth2 authentication failures.
 *
 * <p>This class is responsible for redirecting the user to a specified frontend URL with an error message
 * when an OAuth2 authentication attempt fails.</p>
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Value("${app.security.oauth2.redirect-uri}")
    private String frontendRedirectUrl;


    /**
     * Handles an authentication failure by redirecting the user to the frontend URL with an error message.
     *
     * @param request   the HttpServletRequest that resulted in an AuthenticationException
     * @param response  the HttpServletResponse to which the redirect will be sent
     * @param exception the exception that caused the invocation of this method
     * @throws IOException if an input or output exception occurs while sending the redirect
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.sendRedirect(frontendRedirectUrl + "#error=access_denied&message=" + exception.getMessage());

    }
}
