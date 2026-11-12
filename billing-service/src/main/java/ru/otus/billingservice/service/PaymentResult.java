package ru.otus.billingservice.service;

import java.math.BigDecimal;

public record PaymentResult(long orderId, long userId, String email, BigDecimal amount,
                            PaymentStatus status, boolean duplicate, String reason) {}
