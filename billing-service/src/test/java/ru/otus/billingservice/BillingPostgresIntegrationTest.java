package ru.otus.billingservice;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.otus.billingservice.domain.AccountEntity;
import ru.otus.billingservice.service.AccountNotFoundException;
import ru.otus.billingservice.service.BillingService;
import ru.otus.billingservice.service.PaymentStatus;
import ru.otus.contracts.OrderCreated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.poll-interval-ms=3600000",
        "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef"
})
class BillingPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("billing");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired BillingService billing;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE outbox_events, transactions, accounts RESTART IDENTITY CASCADE").update();
    }

    @Test
    void createsAccountOnlyOnceAndPersistsDeposits() {
        billing.createAccount(7);
        billing.createAccount(7);
        billing.deposit(7, new BigDecimal("20.25"));
        assertThat(billing.getAccount(7).balance()).isEqualByComparingTo("20.25");
        assertThat(jdbc.sql("SELECT count(*) FROM accounts WHERE user_id = 7").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM transactions WHERE type = 'DEPOSIT'").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void rejectsZeroAndNegativeDepositsAndMissingAccounts() {
        billing.createAccount(7);
        assertThatThrownBy(() -> billing.deposit(7, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> billing.deposit(7, new BigDecimal("-1.00"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> billing.deposit(7, new BigDecimal("1.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> billing.getAccount(8)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> billing.charge(10, 8, new BigDecimal("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void succeedsOnceAndDoesNotChangeBalanceForInsufficientFunds() {
        billing.createAccount(7);
        billing.deposit(7, new BigDecimal("10.00"));
        OrderCreated affordable = order(100, 7, "7.00");
        var first = billing.processOrder(affordable);
        var retry = billing.processOrder(affordable);
        var rejected = billing.processOrder(order(101, 7, "8.00"));

        assertThat(first.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(first.duplicate()).isFalse();
        assertThat(retry.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(retry.duplicate()).isTrue();
        assertThat(rejected.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(billing.getAccount(7).balance()).isEqualByComparingTo("3.00");
        assertThat(jdbc.sql("SELECT count(*) FROM transactions WHERE order_id = 100").query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE topic IN ('payments.succeeded','payments.failed')")
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void concurrentChargesCannotOverdrawAccount() throws Exception {
        billing.createAccount(7);
        billing.deposit(7, new BigDecimal("10.00"));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> billing.charge(201, 7, new BigDecimal("8.00")), executor);
            var second = CompletableFuture.supplyAsync(() -> billing.charge(202, 7, new BigDecimal("8.00")), executor);
            List<PaymentStatus> statuses = List.of(first.get().status(), second.get().status());
            assertThat(statuses).containsExactlyInAnyOrder(PaymentStatus.SUCCESS, PaymentStatus.FAILED);
        }
        AccountEntity account = billing.getAccount(7);
        assertThat(account.balance()).isEqualByComparingTo("2.00");
    }

    private static OrderCreated order(long orderId, long userId, String amount) {
        return new OrderCreated("event-" + orderId, java.time.Instant.now(), orderId, userId,
                "user@example.com", new BigDecimal(amount));
    }
}
