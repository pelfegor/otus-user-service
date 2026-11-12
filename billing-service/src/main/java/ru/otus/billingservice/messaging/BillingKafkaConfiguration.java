package ru.otus.billingservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;
import ru.otus.billingservice.service.AccountNotFoundException;
import ru.otus.billingservice.service.BillingService;
import ru.otus.contracts.OrderCreated;

@Configuration
public class BillingKafkaConfiguration {
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> billingOrderKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper mapper,
            BillingService billing) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        var deadLetterRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        var handler = new DefaultErrorHandler((record, exception) -> {
            Throwable cause = exception;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            if (cause instanceof AccountNotFoundException) {
                try {
                    billing.failOrderForMissingAccount(mapper.readValue((String) record.value(), OrderCreated.class));
                } catch (Exception recoveryFailure) {
                    throw new IllegalStateException("Could not publish ACCOUNT_NOT_FOUND payment result", recoveryFailure);
                }
            } else {
                deadLetterRecoverer.accept(record, exception);
            }
        }, new FixedBackOff(1_000L, 4L));
        factory.setCommonErrorHandler(handler);
        return factory;
    }
}
