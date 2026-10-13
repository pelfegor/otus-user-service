package ru.otus.orderservice;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.service.OrderService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.poll-interval-ms=3600000",
        "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef"
})
class OrderPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders").withUsername("orders").withPassword("orders");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired OrderService orders;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() { jdbc.sql("TRUNCATE outbox_events, payment_events, customer_orders RESTART IDENTITY CASCADE").update(); }

    @Test
    void liquibaseCreatesSchemaAndPaymentEventsMoveOrderOnlyOnce() {
        OrderEntity created = orders.create(7, "buyer@example.com", new BigDecimal("50.00"));
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE topic='orders.created'")
                .query(Long.class).single()).isEqualTo(1L);

        orders.applyPayment("payment-1", created.id(), 7, new BigDecimal("50.00"), "PAID");
        orders.applyPayment("payment-1", created.id(), 7, new BigDecimal("50.00"), "PAID");
        orders.applyPayment("payment-2", created.id(), 7, new BigDecimal("50.00"), "PAYMENT_FAILED");

        assertThat(orders.getOwned(created.id(), 7).status()).isEqualTo("PAID");
        assertThat(jdbc.sql("SELECT count(*) FROM payment_events WHERE order_id=:id").param("id", created.id())
                .query(Long.class).single()).isEqualTo(2L);
    }

    @Test
    void failedPaymentAndForeignOrderAccessAreHandled() {
        OrderEntity created = orders.create(7, "buyer@example.com", new BigDecimal("50.00"));
        orders.applyPayment("payment-failed", created.id(), 7, new BigDecimal("50.00"), "PAYMENT_FAILED");
        assertThat(orders.getOwned(created.id(), 7).status()).isEqualTo("PAYMENT_FAILED");
        assertThatThrownBy(() -> orders.getOwned(created.id(), 8)).isInstanceOf(AccessDeniedException.class);
    }
}
