package ru.otus.userservice.dto;

public record UserResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone
) {

    public static UserResponse from(UserDetails user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                user.email(),
                user.phone()
        );
    }
}
