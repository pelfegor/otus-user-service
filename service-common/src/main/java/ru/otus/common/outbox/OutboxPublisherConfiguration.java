package ru.otus.common.outbox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableScheduling
@Import(OutboxStore.class)
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherConfiguration {
    @Bean
    OutboxPublisher outboxPublisher(OutboxDeliveryService deliveryService) {
        return new OutboxPublisher(deliveryService);
    }

    @Bean
    OutboxDeliveryService outboxDeliveryService(org.springframework.jdbc.core.simple.JdbcClient jdbc,
                                                 KafkaTemplate<String, String> kafkaTemplate) {
        return new OutboxDeliveryService(jdbc, kafkaTemplate);
    }
}
