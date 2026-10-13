package ru.otus.userservice.dto;

public record UpdateUserCommand(
        String username,
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
