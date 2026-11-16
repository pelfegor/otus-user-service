package ru.otus.orderservice;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.service.OrderService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.security.access.AccessDeniedException;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.poll-interval-ms=3600000",
        "security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef"
})
@Import(OrderPostgresIntegrationTest.TestRestClientConfiguration.class)
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
    @Autowired RestClient.Builder restClientBuilder;
    private MockRestServiceServer participants;

    @TestConfiguration
    static class TestRestClientConfiguration {
        @Bean
        @Primary
        RestClient.Builder sagaRestClientBuilder() {
            return RestClient.builder();
        }
    }

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE outbox_events, payment_events, customer_orders RESTART IDENTITY CASCADE").update();
        participants = MockRestServiceServer.bindTo(restClientBuilder).build();
        expectSuccess("http://localhost:8001/billing/orders/1/charge", HttpMethod.POST);
        expectSuccess("http://localhost:8003/inventory/reservations", HttpMethod.POST);
        expectCourier("http://localhost:8004/delivery/reservations");
    }

    @Test
    void liquibaseCreatesSchemaAndSagaPersistsConfirmedOrderWithoutPublishingLegacyCreateEvent() {
        OrderEntity created = orders.create(7, "buyer@example.com", "test-token", "SKU-1", 1,
                "slot-1", new BigDecimal("50.00"));
        assertThat(created.status()).isEqualTo("CONFIRMED");
        assertThat(created.sku()).isEqualTo("SKU-1");
        assertThat(created.quantity()).isEqualTo(1);
        assertThat(created.courierId()).isEqualTo("courier-001");
        assertThat(jdbc.sql("SELECT count(*) FROM order_saga_steps WHERE order_id=:id AND state='COMPLETED'")
                .param("id", created.id()).query(Long.class).single()).isEqualTo(3L);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE topic='orders.created'")
                .query(Long.class).single()).isZero();
        participants.verify();
    }

    @Test
    void legacyCreateRetainsAcceptedOrderAndOutboxEvent() {
        OrderEntity created = orders.createLegacy(7, "buyer@example.com", new BigDecimal("50.00"));
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox_events WHERE topic='orders.created'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void foreignOrderAccessIsDenied() {
        OrderEntity created = orders.create(7, "buyer@example.com", "test-token", "SKU-1", 1,
                "slot-1", new BigDecimal("50.00"));
        assertThatThrownBy(() -> orders.getOwned(created.id(), 8)).isInstanceOf(AccessDeniedException.class);
        participants.verify();
    }

    private void expectSuccess(String url, HttpMethod method) {
        participants.expect(MockRestRequestMatchers.requestTo(url))
                .andExpect(MockRestRequestMatchers.method(method))
                .andRespond(MockRestResponseCreators.withSuccess());
    }

    private void expectCourier(String url) {
        participants.expect(MockRestRequestMatchers.requestTo(url))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"orderId\":1,\"courierId\":\"courier-001\",\"slot\":\"slot-1\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }
}
