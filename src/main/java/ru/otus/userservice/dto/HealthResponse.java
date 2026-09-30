package ru.otus.userservice.dto;

public record HealthResponse(String status) {

    public static HealthResponse ok() {
        return new HealthResponse("OK");
    }
}
