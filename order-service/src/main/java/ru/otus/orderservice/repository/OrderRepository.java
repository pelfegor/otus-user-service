package ru.otus.orderservice.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.otus.orderservice.domain.OrderEntity;

@Repository
public class OrderRepository {
    private final JdbcClient jdbc;
    public OrderRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public OrderEntity create(long userId, String email, String sku, int quantity, String slot, BigDecimal price) {
        return jdbc.sql("INSERT INTO customer_orders(user_id, email, sku, quantity, delivery_slot, price) VALUES (:userId, :email, :sku, :quantity, :slot, :price) " +
                        "RETURNING id, user_id AS userId, email, sku, quantity, delivery_slot AS deliverySlot, courier_id AS courierId, price, status, created_at AS createdAt, updated_at AS updatedAt")
                .param("userId", userId).param("email", email).param("sku", sku).param("quantity", quantity).param("slot", slot).param("price", price)
                .query(OrderEntity.class).single();
    }

    public Optional<OrderEntity> findById(long id) {
        return jdbc.sql("SELECT id, user_id AS userId, email, sku, quantity, delivery_slot AS deliverySlot, courier_id AS courierId, price, status, created_at AS createdAt, updated_at AS updatedAt " +
                        "FROM customer_orders WHERE id = :id")
                .param("id", id).query(OrderEntity.class).optional();
    }

    public void setCourierId(long id, String courierId) {
        jdbc.sql("UPDATE customer_orders SET courier_id=:courierId, updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("courierId", courierId).param("id", id).update();
    }

    public void recordSagaStep(long orderId, String step, String state, String error) {
        jdbc.sql("INSERT INTO order_saga_steps(order_id, step, state, error) VALUES (:orderId, :step, :state, :error) " +
                        "ON CONFLICT (order_id, step) DO UPDATE SET state=EXCLUDED.state, error=EXCLUDED.error, updated_at=CURRENT_TIMESTAMP")
                .param("orderId", orderId).param("step", step).param("state", state).param("error", error).update();
    }

    public List<SagaStep> findSagaSteps(long orderId) {
        return jdbc.sql("SELECT step, state, error, updated_at AS updatedAt FROM order_saga_steps " +
                        "WHERE order_id=:orderId ORDER BY updated_at, step")
                .param("orderId", orderId).query(SagaStep.class).list();
    }

    public record SagaStep(String step, String state, String error, OffsetDateTime updatedAt) {}

    public void setStatus(long id, String status) {
        jdbc.sql("UPDATE customer_orders SET status=:status, updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("status", status).param("id", id).update();
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
