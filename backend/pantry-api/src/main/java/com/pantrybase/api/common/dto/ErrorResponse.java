package com.pantrybase.api.common.dto;

import java.time.Instant;

/**
 * Represents the structure of an error response returned by the API.
 *
 * @param timestamp The timestamp when the error occurred.
 * @param status    The HTTP status code of the error.
 * @param error     A brief description of the error.
 * @param message   A detailed message explaining the error.
 * @param path      The request path that caused the error.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {}
