package ru.otus.orderservice.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.otus.orderservice.domain.OrderEntity;

@Repository
public class OrderRepository {
    private final JdbcClient jdbc;
    public OrderRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public OrderEntity create(long userId, String email, BigDecimal price) {
        return jdbc.sql("INSERT INTO customer_orders(user_id, email, price) VALUES (:userId, :email, :price) " +
                        "RETURNING id, user_id AS userId, email, price, status, created_at AS createdAt, updated_at AS updatedAt")
                .param("userId", userId).param("email", email).param("price", price)
                .query(OrderEntity.class).single();
    }

    public Optional<OrderEntity> findById(long id) {
        return jdbc.sql("SELECT id, user_id AS userId, email, price, status, created_at AS createdAt, updated_at AS updatedAt " +
                        "FROM customer_orders WHERE id = :id")
                .param("id", id).query(OrderEntity.class).optional();
    }

    public int applyPayment(long orderId, String status) {
        return jdbc.sql("UPDATE customer_orders SET status = :status, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = :id AND status = 'PENDING'")
                .param("status", status).param("id", orderId).update();
    }

    public int recordPaymentEvent(String eventId, long orderId) {
        return jdbc.sql("INSERT INTO payment_events(event_id, order_id) VALUES (:eventId, :orderId) " +
                        "ON CONFLICT (event_id) DO NOTHING")
                .param("eventId", eventId).param("orderId", orderId).update();
    }
}
