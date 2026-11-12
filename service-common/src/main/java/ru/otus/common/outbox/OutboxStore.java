package ru.otus.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxStore {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public OutboxStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String topic, String key, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            jdbc.sql("INSERT INTO outbox_events(topic, event_key, payload) VALUES (:topic, :key, CAST(:payload AS jsonb))")
                    .param("topic", topic).param("key", key).param("payload", payload).update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event", e);
        }
    }
}
