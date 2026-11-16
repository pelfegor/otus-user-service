package ru.otus.orderservice.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderEntity(Long id, Long userId, String email, String sku, Integer quantity,
                          String deliverySlot, String courierId, BigDecimal price,
                          String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
