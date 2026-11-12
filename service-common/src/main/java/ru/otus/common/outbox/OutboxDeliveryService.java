package ru.otus.common.outbox;

import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDeliveryService {
    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;

    OutboxDeliveryService(JdbcClient jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Transactional
    public void publishBatch() {
        for (int i = 0; i < 50; i++) {
            OutboxRow row = jdbc.sql("SELECT id, topic, event_key, payload::text AS payload, attempts FROM outbox_events " +
                            "WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP " +
                            "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
                    .query(OutboxRow.class).optional().orElse(null);
            if (row == null) return;
            try {
                kafka.send(row.topic(), row.eventKey(), row.payload()).get(10, TimeUnit.SECONDS);
                jdbc.sql("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP, last_error = NULL WHERE id = :id")
                        .param("id", row.id()).update();
            } catch (Exception e) {
                long delaySeconds = Math.min(60, 1L << Math.min(row.attempts(), 6));
                jdbc.sql("UPDATE outbox_events SET attempts = attempts + 1, next_attempt_at = CURRENT_TIMESTAMP + " +
                                "(:delay * INTERVAL '1 second'), last_error = :error WHERE id = :id")
                        .param("delay", delaySeconds).param("error", errorMessage(e)).param("id", row.id()).update();
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.substring(0, Math.min(message.length(), 1000));
    }

    private record OutboxRow(long id, String topic, String eventKey, String payload, int attempts) {}
}
