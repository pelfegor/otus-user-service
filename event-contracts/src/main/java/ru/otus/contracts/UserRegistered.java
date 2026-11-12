package ru.otus.contracts;

import java.time.Instant;

public record UserRegistered(String eventId, Instant occurredAt, Long userId, String email) {}
