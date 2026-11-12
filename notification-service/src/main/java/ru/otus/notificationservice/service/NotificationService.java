package ru.otus.notificationservice.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.notificationservice.domain.NotificationEntity;
import ru.otus.notificationservice.repository.NotificationRepository;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    public NotificationService(NotificationRepository notifications) { this.notifications = notifications; }

    @Transactional
    public void paymentSucceeded(String eventId, long userId, long orderId, String recipient, BigDecimal amount) {
        notifications.save(eventId, userId, orderId, recipient, amount, "Order payment succeeded",
                "Your order " + orderId + " for " + amount + " has been paid successfully.");
    }

    @Transactional
    public void paymentFailed(String eventId, long userId, long orderId, String recipient, BigDecimal amount,
                              String reason) {
        notifications.save(eventId, userId, orderId, recipient, amount, "Order payment failed",
                "Your order " + orderId + " for " + amount + " could not be paid: " +
                        (reason == null ? "payment was declined" : reason) + ".");
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> findForUser(long userId) { return notifications.findByUserId(userId); }
}
