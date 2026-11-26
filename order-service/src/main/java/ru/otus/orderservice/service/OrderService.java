package ru.otus.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.common.outbox.OutboxStore;
import ru.otus.contracts.OrderCreated;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.repository.OrderRepository;
import ru.otus.orderservice.repository.OrderRepository.SagaStep;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final OutboxStore outbox;
    private final RestClient.Builder clients;
    private final String billingUrl, inventoryUrl, deliveryUrl;

    public OrderService(OrderRepository orders, OutboxStore outbox, RestClient.Builder clients,
            @Value("${saga.billing-url:http://localhost:8001}") String billingUrl,
            @Value("${saga.inventory-url:http://localhost:8003}") String inventoryUrl,
            @Value("${saga.delivery-url:http://localhost:8004}") String deliveryUrl) {
        this.orders = orders;
        this.outbox = outbox;
        this.clients = clients;
        this.billingUrl = billingUrl; this.inventoryUrl = inventoryUrl; this.deliveryUrl = deliveryUrl;
    }

    @Transactional
    public OrderEntity createLegacy(long userId, String email, BigDecimal price) {
        validatePrice(price);
        if (email == null || email.isBlank()) throw new IllegalArgumentException("JWT must contain user email");
        OrderEntity order = orders.create(userId, email, "legacy", 1, "legacy", price);
        outbox.enqueue("orders.created", Long.toString(order.id()), new OrderCreated(
                UUID.randomUUID().toString(), Instant.now(), order.id(), userId, email, price));
        return order;
    }

    public OrderEntity create(long userId, String email, String token, String sku, int quantity, String slot, BigDecimal price) {
        validatePrice(price);
        if (email == null || email.isBlank()) throw new IllegalArgumentException("JWT must contain user email");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
        OrderEntity order = orders.create(userId, email, sku, quantity, slot, price);
        boolean charged=false, stock=false, courier=false;
        try {
            orders.recordSagaStep(order.id(), "BILLING", "STARTED", null);
            charged=true;
            call(billingUrl, "/billing/orders/"+order.id()+"/charge", token, new Money(price));
            orders.recordSagaStep(order.id(), "BILLING", "COMPLETED", null);
            orders.recordSagaStep(order.id(), "INVENTORY", "STARTED", null);
            stock=true;
            call(inventoryUrl, "/inventory/reservations", token, new Reservation(order.id(), sku, quantity));
            orders.recordSagaStep(order.id(), "INVENTORY", "COMPLETED", null);
            orders.recordSagaStep(order.id(), "DELIVERY", "STARTED", null);
            courier=true;
            CourierReservation reservedCourier = reserveCourier(order.id(), slot, token);
            orders.setCourierId(order.id(), reservedCourier.courierId());
            orders.recordSagaStep(order.id(), "DELIVERY", "COMPLETED", null);
            orders.setStatus(order.id(), "CONFIRMED");
        } catch (RuntimeException failure) {
            List<String> compensationFailures = new ArrayList<>();
            if (courier) compensate(order.id(), "DELIVERY", "delivery", deliveryUrl,
                    "/delivery/reservations/"+order.id(), token, compensationFailures);
            if (stock) compensate(order.id(), "INVENTORY", "inventory", inventoryUrl,
                    "/inventory/reservations/"+order.id(), token, compensationFailures);
            if (charged) compensate(order.id(), "BILLING", "billing", billingUrl,
                    "/billing/orders/"+order.id()+"/refund", token, compensationFailures);
            orders.setStatus(order.id(), compensationFailures.isEmpty() ? "FAILED" : "COMPENSATION_REQUIRED");
            throw new SagaFailedException(order.id(), failure.getMessage(), compensationFailures);
        }
        return orders.findById(order.id()).orElseThrow();
    }

    private void call(String base, String path, String token, Object body) {
        clients.build().post().uri(base+path).header("Authorization", "Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }
    private CourierReservation reserveCourier(long orderId, String slot, String token) {
        CourierReservation reservation = clients.build().post().uri(deliveryUrl+"/delivery/reservations")
                .header("Authorization", "Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .body(new SlotReservation(orderId, slot)).retrieve().body(CourierReservation.class);
        if (reservation == null || reservation.courierId() == null || reservation.courierId().isBlank())
            throw new IllegalStateException("Delivery service did not assign a courier");
        return reservation;
    }
    private void compensate(long orderId, String stepCode, String step, String base, String path,
                            String token, List<String> failures) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                clients.build().delete().uri(base+path).header("Authorization", "Bearer "+token)
                        .retrieve().toBodilessEntity();
                if ("DELIVERY".equals(stepCode)) orders.setCourierId(orderId, null);
                orders.recordSagaStep(orderId, stepCode, "COMPENSATED", null);
                return;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt < 3) {
                    try { Thread.sleep(100L * attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        lastFailure = new IllegalStateException("Compensation interrupted", interrupted);
                        break;
                    }
                }
            }
        }
        String error = step + ": " + (lastFailure == null ? "unknown error" : lastFailure.getMessage());
        failures.add(error);
        try {
            orders.recordSagaStep(orderId, stepCode, "COMPENSATION_FAILED", error);
        } catch (RuntimeException stateFailure) {
            failures.add(step + " saga-state update: " + stateFailure.getMessage());
        }
    }
    public record Money(BigDecimal amount) {}
    public record Reservation(long orderId, String sku, int quantity) {}
    public record SlotReservation(long orderId, String slot) {}
    public record CourierReservation(long orderId, String courierId, String slot) {}

    @Transactional(readOnly = true)
    public OrderEntity getOwned(long id, long userId) {
        OrderEntity order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (!order.userId().equals(userId)) throw new AccessDeniedException("Order belongs to another user");
        return order;
    }

    @Transactional(readOnly = true)
    public SagaState getSagaStateOwned(long id, long userId) {
        OrderEntity order = getOwned(id, userId);
        return new SagaState(order.status(), orders.findSagaSteps(id));
    }

    public record SagaState(String orderStatus, List<SagaStep> steps) {}

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
