package com.pantrybase.api.common.security;

import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.common.dto.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Custom AuthenticationEntryPoint that handles unauthorized access attempts.
 *
 * <p>This class is responsible for sending a JSON response with an appropriate error message
 * when an unauthenticated user tries to access a protected resource.</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handles an authentication failure by sending a JSON response with an error message.
     *
     * @param request       the HttpServletRequest that resulted in an AuthenticationException
     * @param response      the HttpServletResponse to which the error response will be written
     * @param authException the exception that caused the invocation of this method
     * @throws IOException if an input or output exception occurs while writing the response
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorResponse body = ErrorResponseFactory.unauthorized(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
