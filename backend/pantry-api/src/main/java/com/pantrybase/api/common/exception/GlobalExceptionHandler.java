package com.pantrybase.api.common.exception;

import com.pantrybase.api.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        Instant.now(), 409, "Conflict",
                        ex.getMessage(), "api/auth/register"));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        Instant.now(), 409, "Conflict",
                        ex.getMessage(), "api/auth/register"));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        Instant.now(), 404, "Not Found",
                        ex.getMessage(), "api/auth/login"));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        Instant.now(), 401, "Unauthorized",
                        ex.getMessage(), "api/auth/refresh"));
    }
}