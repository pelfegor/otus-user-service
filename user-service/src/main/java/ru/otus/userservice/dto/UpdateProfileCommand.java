package ru.otus.userservice.dto;

public record UpdateProfileCommand(
        String firstName,
        String lastName,
        String email,
        String phone
) {
}
