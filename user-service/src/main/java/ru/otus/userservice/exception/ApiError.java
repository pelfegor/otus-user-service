package ru.otus.userservice.exception;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors
) {

    public ApiError {
        validationErrors = Map.copyOf(validationErrors);
    }

    public static ApiError of(HttpStatus status, String message) {
        return new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                status.getReasonPhrase(),
                message,
                Map.of()
        );
    }

    public static ApiError validation(
            HttpStatus status,
            String message,
            Map<String, String> validationErrors
    ) {
        return new ApiError(
                OffsetDateTime.now(ZoneOffset.UTC),
                status.value(),
                status.getReasonPhrase(),
                message,
                validationErrors
        );
    }
}
