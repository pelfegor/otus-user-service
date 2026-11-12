package ru.otus.userservice.dto;

public record RegisterCommand(
        String username,
        String password,
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
