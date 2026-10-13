package ru.otus.contracts;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentFailed(String eventId, Instant occurredAt, Long orderId, Long userId,
                            String email, BigDecimal amount, String reason) {}
