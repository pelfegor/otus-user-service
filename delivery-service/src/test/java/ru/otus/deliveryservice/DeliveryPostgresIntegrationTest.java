package ru.otus.deliveryservice;

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
import org.springframework.web.server.ResponseStatusException;
import ru.otus.deliveryservice.service.DeliveryService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef")
class DeliveryPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("delivery").withUsername("delivery").withPassword("delivery");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired DeliveryService delivery;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clearReservations() { jdbc.sql("TRUNCATE delivery_reservations").update(); }

    @Test
    void assignsCourierPersistsReservationAndReusesCourierAfterRelease() {
        var first = delivery.reserve(81, "slot-1");
        assertThat(first.courierId()).isEqualTo("courier-001");
        assertThat(delivery.reserve(81, "slot-1")).isEqualTo(first);
        assertThatThrownBy(() -> delivery.reserve(82, "slot-1")).isInstanceOf(ResponseStatusException.class);
        delivery.release(81);
        assertThat(delivery.reserve(82, "slot-1").courierId()).isEqualTo("courier-001");
    }
}
