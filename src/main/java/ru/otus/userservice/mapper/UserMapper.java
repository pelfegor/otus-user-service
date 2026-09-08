package ru.otus.userservice.mapper;

import ru.otus.userservice.dto.CreateUserRequest;
import ru.otus.userservice.dto.UpdateUserRequest;
import ru.otus.userservice.dto.UserResponse;
import ru.otus.userservice.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static User fromCreateRequest(CreateUserRequest request) {
        return new User(
                null,
                request.username(),
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone()
        );
    }

    public static User fromUpdateRequest(Long id, UpdateUserRequest request) {
        return new User(
                id,
                request.username(),
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone()
        );
    }

    public static UserResponse toResponse(User user) {
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