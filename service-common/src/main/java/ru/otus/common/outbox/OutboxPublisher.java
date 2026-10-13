package ru.otus.common.outbox;

import org.springframework.scheduling.annotation.Scheduled;

public class OutboxPublisher {
    private final OutboxDeliveryService deliveryService;

    OutboxPublisher(OutboxDeliveryService deliveryService) { this.deliveryService = deliveryService; }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    public void publishPending() { deliveryService.publishBatch(); }
}
