package com.pantrybase.api.common.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Factory class for creating ErrorResponse instances.
 */
public class ErrorResponseFactory {

    private ErrorResponseFactory() {}

    /**
     * Creates an ErrorResponse instance based on the provided HTTP status, message, and request.
     *
     * @param status  the HTTP status
     * @param message the error message
     * @param request the HTTP servlet request
     * @return an ErrorResponse instance
     */
    public static ErrorResponse of(HttpStatus status, String message, HttpServletRequest request) {
        return new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
    }

    /**
     * Creates an ErrorResponse instance for unauthorized access based on the provided request.
     *
     * @param request the HTTP servlet request
     * @return an ErrorResponse instance indicating unauthorized access
     */
    public static ErrorResponse unauthorized(HttpServletRequest request) {
        return of(HttpStatus.UNAUTHORIZED, "Authentication required", request);
    }
}
