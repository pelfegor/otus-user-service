package ru.otus.userservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_ERROR_MESSAGE =
            "Request validation failed";
    private static final String MALFORMED_REQUEST_MESSAGE = "Malformed request";
    private static final String DUPLICATE_USER_MESSAGE =
            "User with the same username or email already exists";
    private static final String DATABASE_ERROR_MESSAGE =
            "Database operation failed";
    private static final String ACCESS_DENIED_MESSAGE = "Access denied";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            UserNotFoundException exception
    ) {
        log.warn("User not found: {}", exception.getMessage());

        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = validationErrors(exception);

        log.warn("Request validation failed: errors={}", errors);

        return ResponseEntity.badRequest()
                .body(ApiError.validation(
                        HttpStatus.BAD_REQUEST,
                        VALIDATION_ERROR_MESSAGE,
                        errors
                ));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey() {
        log.warn("Duplicate user data");

        return errorResponse(HttpStatus.CONFLICT, DUPLICATE_USER_MESSAGE);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDatabaseError(
            DataAccessException exception
    ) {
        log.error("Database error", exception);

        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                DATABASE_ERROR_MESSAGE
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException exception
    ) {
        log.warn("Authentication failed");

        return errorResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception
    ) {
        log.warn("Access denied: {}", exception.getMessage());

        return errorResponse(HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest() {
        return errorResponse(HttpStatus.BAD_REQUEST, MALFORMED_REQUEST_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(
            Exception exception
    ) {
        log.error("Unexpected application error", exception);

        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_ERROR_MESSAGE
        );
    }

    private ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status, message));
    }

    private Map<String, String> validationErrors(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        return errors;
    }
}
