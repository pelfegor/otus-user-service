package ru.otus.inventoryservice;

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
import ru.otus.inventoryservice.service.InventoryService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef")
class InventoryPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory").withUsername("inventory").withPassword("inventory");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired InventoryService inventory;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void resetStock() {
        jdbc.sql("TRUNCATE inventory_reservations").update();
        jdbc.sql("UPDATE inventory_stock SET available=100 WHERE sku='SKU-001'").update();
    }

    @Test
    void reservationPersistsAndReleaseRestoresStockExactlyOnce() {
        inventory.reserve(77, "SKU-001", 8);
        inventory.reserve(77, "SKU-001", 8);
        assertThat(available()).isEqualTo(92);
        assertThatThrownBy(() -> inventory.reserve(77, "SKU-001", 9)).isInstanceOf(ResponseStatusException.class);
        inventory.release(77);
        inventory.release(77);
        assertThat(available()).isEqualTo(100);
    }

    private int available() {
        return jdbc.sql("SELECT available FROM inventory_stock WHERE sku='SKU-001'").query(Integer.class).single();
    }
}
