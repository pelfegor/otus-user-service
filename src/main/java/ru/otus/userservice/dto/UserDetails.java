package ru.otus.userservice.dto;

import ru.otus.userservice.entity.User;

public record UserDetails(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        String phone
) {

    public static UserDetails from(User user) {
        return new UserDetails(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                user.email(),
                user.phone()
        );
    }
}
