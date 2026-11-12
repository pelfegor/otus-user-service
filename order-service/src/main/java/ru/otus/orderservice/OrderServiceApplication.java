package ru.otus.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import ru.otus.common.kafka.KafkaConsumerConfiguration;
import ru.otus.common.outbox.OutboxPublisherConfiguration;
import ru.otus.common.security.JwtSecurityConfiguration;

@SpringBootApplication
@EnableKafka
@Import({JwtSecurityConfiguration.class, OutboxPublisherConfiguration.class, KafkaConsumerConfiguration.class})
public class OrderServiceApplication {
    public static void main(String[] args) { SpringApplication.run(OrderServiceApplication.class, args); }
}
