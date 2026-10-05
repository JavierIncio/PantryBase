package com.pantrybase.api.common.exception;

import com.pantrybase.api.catalog.exception.FdcProviderException;
import com.pantrybase.api.catalog.exception.DensityClassNotFoundException;
import com.pantrybase.api.catalog.exception.DensityClassRequiredException;
import com.pantrybase.api.catalog.exception.IngredientNotFoundException;
import com.pantrybase.api.catalog.exception.MeasureConversionNotFoundException;
import com.pantrybase.api.catalog.exception.UnknownUnitException;
import com.pantrybase.api.catalog.exception.UnsupportedConversionException;
import com.pantrybase.api.common.dto.ErrorResponse;
import com.pantrybase.api.common.dto.ErrorResponseFactory;
import com.pantrybase.api.pantry.exception.IngredientAlreadyInPantryException;
import com.pantrybase.api.pantry.exception.IngredientNotInCatalogException;
import com.pantrybase.api.pantry.exception.InsufficientStockException;
import com.pantrybase.api.pantry.exception.PantryItemNotFoundException;
import com.pantrybase.api.pantry.exception.ZeroAdjustmentException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
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

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
    public ResponseEntity<ErrorResponse> handleInvalidPasswordResetTokenException(InvalidPasswordResetTokenException ex,
                                                                                  HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDensityClassRequiredException(DensityClassRequiredException ex,
                                                                                   HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUnknownUnitException(UnknownUnitException ex,
                                                                    HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUnsupportedConversionException(UnsupportedConversionException ex,
                                                                              HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseFactory.of(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleDensityClassNotFoundException(DensityClassNotFoundException ex,
                                                                                  HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleMeasureConversionNotFoundException(MeasureConversionNotFoundException ex,
                                                                                  HttpServletRequest request) {
        log.error("Measure conversion not found: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseFactory.of(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request));
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

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleSmtpException(SmtpException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseFactory.of(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to send password reset email", request));
    }

    /** Maps an unknown catalog ingredient (absent in the provider) to 404. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleIngredientNotFoundException(IngredientNotFoundException ex,
                                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseFactory.of(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    /** Maps provider failures (FDC down, rate limited) to 502 Bad Gateway. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleFdcProviderException(FdcProviderException ex,
                                                                           HttpServletRequest request) {
        log.error("External catalog provider failed: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_GATEWAY, ex.getMessage(), request));
    }

    /** Maps invalid input arguments (e.g. blank search query) to 400. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    /**
     * Maps an ingredient missing from the local catalog to 404.
     *
     * <p>404 and not 400: the request names a food that this instance cannot resolve,
     * and the message tells the client which call fixes it.</p>
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleIngredientNotInCatalogException(IngredientNotInCatalogException ex,
                                                                                HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseFactory.of(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    /**
     * Maps a repeated add to 409.
     *
     * <p>The pantry holds one line per ingredient, so a second add is a conflict with a
     * state the caller could not see, and the message points at the endpoint that does
     * what it wanted.</p>
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleIngredientAlreadyInPantryException(IngredientAlreadyInPantryException ex,
                                                                                 HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    /** Maps a missing pantry item, including one owned by somebody else, to 404. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handlePantryItemNotFoundException(PantryItemNotFoundException ex,
                                                                           HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseFactory.of(HttpStatus.NOT_FOUND, ex.getMessage(), request));
    }

    /**
     * Maps an adjustment that would leave no stock to 409.
     *
     * <p>409 and not 400: the request itself is well formed, what fails is the state it
     * was applied to. It is the same conflict as the duplicate add — the state the
     * caller could not see is what does not admit what they asked for.</p>
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(InsufficientStockException ex,
                                                                          HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT, ex.getMessage(), request));
    }

    /** Maps an adjustment of zero to 400: it is a malformed movement, not a conflict. */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleZeroAdjustmentException(ZeroAdjustmentException ex,
                                                                       HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseFactory.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request));
    }

    /**
     * Maps a lost optimistic-lock race to 409.
     *
     * <p>Handled on the superclass rather than JPA's own subtype so the mapping does not
     * depend on which persistence provider raises it, and so a lost update over any
     * other versioned row reports the same honest conflict instead of a 500.</p>
     *
     * <p>Reporting 500 would blame the server for the one situation the caller can act
     * on by simply retrying against fresh numbers.</p>
     */
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> handleOptimisticLockFailure(OptimisticLockingFailureException ex,
                                                                     HttpServletRequest request) {
        log.debug("Optimistic lock conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponseFactory.of(HttpStatus.CONFLICT,
                        "The item was modified concurrently; read it again and retry", request));
    }
}
