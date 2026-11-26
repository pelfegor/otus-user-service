package ru.otus.inventoryservice.repository;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    private final JdbcClient jdbc;
    public InventoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Reservation> findReservation(long orderId) {
        return jdbc.sql("SELECT order_id AS orderId, sku, quantity FROM inventory_reservations WHERE order_id=:orderId")
                .param("orderId", orderId).query(Reservation.class).optional();
    }

    public void lockOrder(long orderId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:orderId)").param("orderId", orderId)
                .query((rs, row) -> Boolean.TRUE).single();
    }

    public Optional<Integer> lockAvailable(String sku) {
        return jdbc.sql("SELECT available FROM inventory_stock WHERE sku=:sku FOR UPDATE")
                .param("sku", sku).query(Integer.class).optional();
    }

    public void reserve(long orderId, String sku, int quantity) {
        jdbc.sql("UPDATE inventory_stock SET available=available-:quantity WHERE sku=:sku")
                .param("quantity", quantity).param("sku", sku).update();
        jdbc.sql("INSERT INTO inventory_reservations(order_id, sku, quantity) VALUES (:orderId, :sku, :quantity)")
                .param("orderId", orderId).param("sku", sku).param("quantity", quantity).update();
    }

    public Optional<Reservation> release(long orderId) {
        return jdbc.sql("DELETE FROM inventory_reservations WHERE order_id=:orderId " +
                        "RETURNING order_id AS orderId, sku, quantity")
                .param("orderId", orderId).query(Reservation.class).optional();
    }

    public void restoreStock(String sku, int quantity) {
        jdbc.sql("UPDATE inventory_stock SET available=available+:quantity WHERE sku=:sku")
                .param("quantity", quantity).param("sku", sku).update();
    }

    public record Reservation(long orderId, String sku, int quantity) {}
}
