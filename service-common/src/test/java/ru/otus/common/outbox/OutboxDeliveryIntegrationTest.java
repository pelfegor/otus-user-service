package ru.otus.common.outbox;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class OutboxDeliveryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("outbox").withUsername("outbox").withPassword("outbox");

    private JdbcClient jdbc;
    private KafkaTemplate<String, String> kafka;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("DROP TABLE IF EXISTS outbox_events").update();
        jdbc.sql("CREATE TABLE outbox_events (id BIGSERIAL PRIMARY KEY, topic VARCHAR(200) NOT NULL, " +
                "event_key VARCHAR(200) NOT NULL, payload JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "published_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "last_error TEXT)").update();
        kafka = mock(KafkaTemplate.class);
    }

    @Test
    void marksEventPublishedOnlyAfterKafkaAcknowledges() throws Exception {
        jdbc.sql("INSERT INTO outbox_events(topic,event_key,payload) VALUES ('topic','key','{}')").update();
        when(kafka.send("topic", "key", "{}")).thenReturn(CompletableFuture.completedFuture(null));

        new OutboxDeliveryService(jdbc, kafka).publishBatch();

        assertThat(jdbc.sql("SELECT published_at FROM outbox_events WHERE id = 1").query(java.time.OffsetDateTime.class).single()).isNotNull();
        assertThat(jdbc.sql("SELECT attempts FROM outbox_events WHERE id = 1").query(Integer.class).single()).isZero();
    }

    @Test
    void failedKafkaSendKeepsEventPendingAndSchedulesRetry() {
        jdbc.sql("INSERT INTO outbox_events(topic,event_key,payload) VALUES ('topic','key','{}')").update();
        when(kafka.send("topic", "key", "{}")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        new OutboxDeliveryService(jdbc, kafka).publishBatch();

        assertThat(jdbc.sql("SELECT published_at FROM outbox_events WHERE id = 1").query(java.time.OffsetDateTime.class).optional()).isEmpty();
        assertThat(jdbc.sql("SELECT attempts FROM outbox_events WHERE id = 1").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT last_error FROM outbox_events WHERE id = 1").query(String.class).single()).contains("broker unavailable");
    }
}
