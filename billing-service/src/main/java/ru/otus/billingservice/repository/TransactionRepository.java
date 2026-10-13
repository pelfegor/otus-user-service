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
}
