package ru.otus.billingservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.otus.billingservice.service.BillingService;
import ru.otus.contracts.OrderCreated;
import ru.otus.contracts.UserRegistered;

@Component
public class BillingEventListener {
    private final ObjectMapper mapper;
    private final BillingService billing;

    public BillingEventListener(ObjectMapper mapper, BillingService billing) {
        this.mapper = mapper;
        this.billing = billing;
    }

    @KafkaListener(topics = "users.registered", groupId = "billing-service")
    public void onUserRegistered(String message) throws Exception {
        UserRegistered event = mapper.readValue(message, UserRegistered.class);
        billing.createAccount(event.userId());
    }

    @KafkaListener(topics = "orders.created", groupId = "billing-service",
            containerFactory = "billingOrderKafkaListenerContainerFactory")
    public void onOrderCreated(String message) throws Exception {
        billing.processOrder(mapper.readValue(message, OrderCreated.class));
    }
}
