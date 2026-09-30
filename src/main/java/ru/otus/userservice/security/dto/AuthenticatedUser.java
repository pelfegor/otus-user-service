package ru.otus.userservice.security.dto;

import ru.otus.userservice.entity.User;

public record AuthenticatedUser(Long id, String username) {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.id(), user.username());
    }
}
