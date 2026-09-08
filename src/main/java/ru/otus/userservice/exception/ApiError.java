package ru.otus.userservice.exception;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> validationErrors
) {

    public static ApiError of(
            int status,
            String error,
            String message
    ) {
        return new ApiError(
                OffsetDateTime.now(),
                status,
                error,
                message,
                Map.of()
        );
    }

    public static ApiError validation(
            int status,
            String error,
            String message,
            Map<String, String> validationErrors
    ) {
        return new ApiError(
                OffsetDateTime.now(),
                status,
                error,
                message,
                validationErrors
        );
    }
}