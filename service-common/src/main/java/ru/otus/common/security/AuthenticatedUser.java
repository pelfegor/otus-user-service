package ru.otus.common.security;

public record AuthenticatedUser(Long userId, String username, String email) {}
