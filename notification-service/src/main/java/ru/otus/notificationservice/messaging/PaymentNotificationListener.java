package ru.otus.notificationservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.otus.contracts.PaymentFailed;
import ru.otus.contracts.PaymentSucceeded;
import ru.otus.notificationservice.service.NotificationService;

@Component
public class PaymentNotificationListener {
    private final ObjectMapper mapper;
    private final NotificationService notifications;
    public PaymentNotificationListener(ObjectMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @KafkaListener(topics = "payments.succeeded", groupId = "notification-service")
    public void onSucceeded(String message) throws Exception {
        PaymentSucceeded event = mapper.readValue(message, PaymentSucceeded.class);
        notifications.paymentSucceeded(event.eventId(), event.userId(), event.orderId(), event.email(), event.amount());
    }

    @KafkaListener(topics = "payments.failed", groupId = "notification-service")
    public void onFailed(String message) throws Exception {
        PaymentFailed event = mapper.readValue(message, PaymentFailed.class);
        notifications.paymentFailed(event.eventId(), event.userId(), event.orderId(), event.email(), event.amount(), event.reason());
    }
}
