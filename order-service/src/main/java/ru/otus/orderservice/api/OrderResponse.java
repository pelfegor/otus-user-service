package ru.otus.orderservice.api;

import java.math.BigDecimal;
import ru.otus.orderservice.domain.OrderEntity;

public record OrderResponse(Long id, BigDecimal price, String status) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.id(), order.price(), order.status());
    }
}
