package ru.otus.contracts;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreated(String eventId, Instant occurredAt, Long orderId, Long userId,
                           String email, BigDecimal amount) {}
