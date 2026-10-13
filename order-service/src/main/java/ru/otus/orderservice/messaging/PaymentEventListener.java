package ru.otus.orderservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.otus.contracts.PaymentFailed;
import ru.otus.contracts.PaymentSucceeded;
import ru.otus.orderservice.service.OrderService;

@Component
public class PaymentEventListener {
    private final ObjectMapper mapper;
    private final OrderService orders;
    public PaymentEventListener(ObjectMapper mapper, OrderService orders) { this.mapper = mapper; this.orders = orders; }

    @KafkaListener(topics = "payments.succeeded", groupId = "order-service")
    public void onSucceeded(String message) throws Exception {
        PaymentSucceeded event = mapper.readValue(message, PaymentSucceeded.class);
        orders.applyPayment(event.eventId(), event.orderId(), event.userId(), event.amount(), "PAID");
    }

    @KafkaListener(topics = "payments.failed", groupId = "order-service")
    public void onFailed(String message) throws Exception {
        PaymentFailed event = mapper.readValue(message, PaymentFailed.class);
        orders.applyPayment(event.eventId(), event.orderId(), event.userId(), event.amount(), "PAYMENT_FAILED");
    }
}
