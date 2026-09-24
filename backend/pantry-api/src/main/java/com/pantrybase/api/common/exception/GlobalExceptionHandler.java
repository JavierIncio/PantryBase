package com.pantrybase.api.common.exception;

import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.common.dto.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralizes exception handling for the REST API.
 *
 * <p>Maps application and validation exceptions to standardized {@link ErrorResponse} objects
 * and appropriate HTTP status codes.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles bean validation failures by returning the field errors as a single message.
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest()
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, message, request));
    }

    /**
     * Maps unknown allergen codes (invalid request values) to a standardized 400 response.
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUnknownAllergenCodeException(UnknownAllergenCodeException ex,
                                                                            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleCurrentPasswordMismatchException(CurrentPasswordMismatchException ex,
                                                                                HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    /**
     * Maps malformed JSON bodies (including unknown enum values) to a standardized 400 response.
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, "Malformed request body", request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex,
                                                                            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponseFactory.unauthorized(request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex,
                                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponseFactory.unauthorized(request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseFactory.of(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException ex,
                                                                              HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex,
                                                                            HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handlePasswordNotSetException(PasswordNotSetException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT, ex.getMessage(), request));
    }
}
