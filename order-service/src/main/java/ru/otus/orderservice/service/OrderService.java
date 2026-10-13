package ru.otus.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.common.outbox.OutboxStore;
import ru.otus.contracts.OrderCreated;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.repository.OrderRepository;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OutboxStore outbox;

    public OrderService(OrderRepository orders, OutboxStore outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public OrderEntity create(long userId, String email, BigDecimal price) {
        validatePrice(price);
        if (email == null || email.isBlank()) throw new IllegalArgumentException("JWT must contain user email");
        OrderEntity order = orders.create(userId, email, price);
        outbox.enqueue("orders.created", Long.toString(order.id()), new OrderCreated(
                UUID.randomUUID().toString(), Instant.now(), order.id(), userId, email, price));
        return order;
    }

    @Transactional(readOnly = true)
    public OrderEntity getOwned(long id, long userId) {
        OrderEntity order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (!order.userId().equals(userId)) throw new AccessDeniedException("Order belongs to another user");
        return order;
    }

    @Transactional
    public void applyPayment(String eventId, long orderId, long userId, BigDecimal amount, String status) {
        if (orders.recordPaymentEvent(eventId, orderId) == 0) return;
        OrderEntity order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.userId().equals(userId) || order.price().compareTo(amount) != 0) {
            throw new IllegalStateException("Payment event does not match order");
        }
        orders.applyPayment(orderId, status);
    }

    private static void validatePrice(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 ||
                amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("price must be positive with at most two decimal places");
        }
    }
}
