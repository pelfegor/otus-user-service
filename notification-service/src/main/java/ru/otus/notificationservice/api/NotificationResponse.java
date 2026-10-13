package ru.otus.notificationservice.api;

import java.math.BigDecimal;
import ru.otus.notificationservice.domain.NotificationEntity;

public record NotificationResponse(Long orderId, String recipient, BigDecimal amount, String subject, String message) {
    public static NotificationResponse from(NotificationEntity entity) {
        return new NotificationResponse(entity.orderId(), entity.recipient(), entity.amount(), entity.subject(), entity.message());
    }
}
