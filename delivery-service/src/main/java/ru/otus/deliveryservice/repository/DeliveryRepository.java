package ru.otus.deliveryservice.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryRepository {
    private final JdbcClient jdbc;
    public DeliveryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Reservation> findReservation(long orderId) {
        return jdbc.sql("SELECT order_id AS orderId, courier_id AS courierId, slot FROM delivery_reservations WHERE order_id=:orderId")
                .param("orderId", orderId).query(Reservation.class).optional();
    }

    public void lockOrder(long orderId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:orderId)").param("orderId", orderId)
                .query((rs, row) -> Boolean.TRUE).single();
    }

    public Optional<String> lockAvailableCourier(String slot) {
        return jdbc.sql("SELECT c.courier_id FROM couriers c " +
                        "WHERE c.enabled=true AND NOT EXISTS " +
                        "(SELECT 1 FROM delivery_reservations r WHERE r.courier_id=c.courier_id AND r.slot=:slot) " +
                        "ORDER BY c.courier_id LIMIT 1 FOR UPDATE OF c SKIP LOCKED")
                .param("slot", slot).query(String.class).optional();
    }

    public void reserve(long orderId, String courierId, String slot) {
        jdbc.sql("INSERT INTO delivery_reservations(order_id, courier_id, slot) VALUES (:orderId, :courierId, :slot)")
                .param("orderId", orderId).param("courierId", courierId).param("slot", slot).update();
    }

    public void release(long orderId) {
        jdbc.sql("DELETE FROM delivery_reservations WHERE order_id=:orderId").param("orderId", orderId).update();
    }

    public record Reservation(long orderId, String courierId, String slot) {}
}
