package ru.otus.notificationservice.repository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.otus.notificationservice.domain.NotificationEntity;

@Repository
public class NotificationRepository {
    private final JdbcClient jdbc;
    public NotificationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void save(String eventId, long userId, long orderId, String recipient, BigDecimal amount,
                     String subject, String message) {
        jdbc.sql("INSERT INTO notifications(event_id, user_id, order_id, recipient, amount, subject, message) " +
                        "VALUES (:eventId, :userId, :orderId, :recipient, :amount, :subject, :message) ON CONFLICT (event_id) DO NOTHING")
                .param("eventId", eventId).param("userId", userId).param("orderId", orderId)
                .param("recipient", recipient).param("amount", amount)
                .param("subject", subject).param("message", message).update();
    }

    public List<NotificationEntity> findByUserId(long userId) {
        return jdbc.sql("SELECT id, event_id AS eventId, user_id AS userId, order_id AS orderId, " +
                        "recipient, amount, subject, message, " +
                        "created_at AS createdAt FROM notifications WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
                .param("userId", userId).query(NotificationEntity.class).list();
    }
}
