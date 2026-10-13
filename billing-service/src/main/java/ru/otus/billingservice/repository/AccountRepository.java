package ru.otus.billingservice.repository;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.otus.billingservice.domain.AccountEntity;

@Repository
public class AccountRepository {
    private final JdbcClient jdbc;
    public AccountRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void createIfAbsent(long userId) {
        jdbc.sql("INSERT INTO accounts(user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING")
            .param("userId", userId).update();
    }

    public Optional<AccountEntity> findByUserId(long userId) {
        return jdbc.sql("SELECT * FROM accounts WHERE user_id = :userId")
            .param("userId", userId).query(AccountEntity.class).optional();
    }

    public Optional<AccountEntity> lockByUserId(long userId) {
        return jdbc.sql("SELECT * FROM accounts WHERE user_id = :userId FOR UPDATE")
            .param("userId", userId).query(AccountEntity.class).optional();
    }

    public void deposit(long accountId, BigDecimal amount) {
        jdbc.sql("UPDATE accounts SET balance = balance + :amount, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
            .param("amount", amount).param("id", accountId).update();
    }

    public int withdrawIfEnough(long accountId, BigDecimal amount) {
        return jdbc.sql("UPDATE accounts SET balance = balance - :amount, updated_at = CURRENT_TIMESTAMP " +
                        "WHERE id = :id AND balance >= :amount")
            .param("amount", amount).param("id", accountId).update();
    }
}
