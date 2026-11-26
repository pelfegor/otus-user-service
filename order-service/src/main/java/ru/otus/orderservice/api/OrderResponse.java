package ru.otus.orderservice.api;

import java.math.BigDecimal;
import ru.otus.orderservice.domain.OrderEntity;

public record OrderResponse(Long id, String sku, Integer quantity, String deliverySlot, String courierId,
                            BigDecimal price, String status) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(order.id(), order.sku(), order.quantity(), order.deliverySlot(), order.courierId(),
                order.price(), order.status());
    }
}
