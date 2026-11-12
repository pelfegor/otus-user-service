package ru.otus.notificationservice.domain;

import java.time.OffsetDateTime;
import java.math.BigDecimal;

public record NotificationEntity(Long id, String eventId, Long userId, Long orderId,
                                 String recipient, BigDecimal amount, String subject, String message,
                                 OffsetDateTime createdAt) {}
