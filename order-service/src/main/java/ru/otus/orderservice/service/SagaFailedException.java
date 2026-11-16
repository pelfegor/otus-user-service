package ru.otus.orderservice.service;

import java.util.List;

public class SagaFailedException extends RuntimeException {
    private final long orderId;

    public SagaFailedException(long orderId, String reason, List<String> compensationFailures) {
        super(compensationFailures.isEmpty()
                ? "Order " + orderId + " failed; completed steps were compensated: " + reason
                : "Order " + orderId + " failed and compensation needs attention: " + compensationFailures);
        this.orderId = orderId;
    }

    public long orderId() { return orderId; }
}
