package ru.otus.billingservice.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.otus.billingservice.domain.TransactionEntity;

@Repository
public class TransactionRepository {
    private final JdbcClient jdbc;
    public TransactionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public boolean lockOrder(long orderId, long userId) {
        jdbc.sql("INSERT INTO payment_orders(order_id, user_id) VALUES (:orderId, :userId) " +
                 "ON CONFLICT (order_id) DO NOTHING")
            .param("orderId", orderId).param("userId", userId).update();
        return jdbc.sql("SELECT cancelled FROM payment_orders WHERE order_id = :orderId AND user_id = :userId FOR UPDATE")
            .param("orderId", orderId).param("userId", userId)
            .query(Boolean.class).optional()
            .orElseThrow(() -> new IllegalArgumentException("orderId already used by a different user"));
    }

    public void markCancelled(long orderId) {
        jdbc.sql("UPDATE payment_orders SET cancelled = TRUE WHERE order_id = :orderId")
            .param("orderId", orderId).update();
    }

    public Optional<TransactionEntity> findByOrderId(long orderId) {
        return jdbc.sql("SELECT * FROM transactions WHERE order_id = :orderId")
            .param("orderId", orderId).query(TransactionEntity.class).optional();
    }

    public void saveDeposit(long accountId, BigDecimal amount) {
        jdbc.sql("INSERT INTO transactions(account_id, amount, type, status) " +
                 "VALUES (:accountId, :amount, 'DEPOSIT', 'SUCCESS')")
            .param("accountId", accountId).param("amount", amount).update();
    }

    public void savePayment(long accountId, long orderId, BigDecimal amount, boolean succeeded) {
        jdbc.sql("INSERT INTO transactions(account_id, order_id, amount, type, status) " +
                 "VALUES (:accountId, :orderId, :amount, 'WITHDRAWAL', :status)")
            .param("accountId", accountId).param("orderId", orderId).param("amount", amount)
            .param("status", succeeded ? "SUCCESS" : "FAILED").update();
    }

    public int markRefunded(long orderId) {
        return jdbc.sql("UPDATE transactions SET status='REFUNDED' WHERE order_id=:orderId AND type='WITHDRAWAL' AND status='SUCCESS'")
                .param("orderId", orderId).update();
    }
}
