package ru.otus.notificationservice;

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
import ru.otus.notificationservice.service.NotificationService;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef"
})
class NotificationPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notifications").withUsername("notifications").withPassword("notifications");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
    }

    @Autowired NotificationService notifications;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() { jdbc.sql("TRUNCATE notifications RESTART IDENTITY").update(); }

    @Test
    void liquibaseCreatesSchemaAndDuplicateEventsRemainScopedToOwner() {
        notifications.paymentSucceeded("evt-success", 5, 20, "user@example.com", new java.math.BigDecimal("5.00"));
        notifications.paymentSucceeded("evt-success", 5, 20, "user@example.com", new java.math.BigDecimal("5.00"));
        notifications.paymentFailed("evt-failed", 5, 21, "user@example.com", new java.math.BigDecimal("15.75"), "INSUFFICIENT_BALANCE");

        assertThat(notifications.findForUser(5)).hasSize(2);
        assertThat(notifications.findForUser(5)).extracting("message")
                .contains("Your order 21 for 15.75 could not be paid: INSUFFICIENT_BALANCE.");
        assertThat(notifications.findForUser(5)).extracting("recipient").containsOnly("user@example.com");
        assertThat(notifications.findForUser(5)).extracting("amount").contains(new java.math.BigDecimal("5.00"), new java.math.BigDecimal("15.75"));
        assertThat(notifications.findForUser(6)).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM notifications").query(Long.class).single()).isEqualTo(2L);
    }
}
