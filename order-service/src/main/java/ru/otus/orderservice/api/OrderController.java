package ru.otus.orderservice.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.common.security.AuthenticatedUser;
import ru.otus.orderservice.service.OrderService;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestHeader("Authorization") String authorization,
                                                 @Valid @RequestBody CreateOrderRequest request) {
        if (request.sku() == null && request.quantity() == null && request.deliverySlot() == null) {
            OrderResponse legacyResponse = OrderResponse.from(orders.createLegacy(user.userId(), user.email(), request.price()));
            return ResponseEntity.accepted().body(legacyResponse);
        }
        if (request.sku() == null || request.sku().isBlank() || request.quantity() == null ||
                request.deliverySlot() == null || request.deliverySlot().isBlank()) {
            throw new IllegalArgumentException("sku, quantity and deliverySlot must be supplied together");
        }
        OrderResponse response = OrderResponse.from(orders.create(user.userId(), user.email(), authorization.substring("Bearer ".length()),
                request.sku(), request.quantity(), request.deliverySlot(), request.price()));
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return OrderResponse.from(orders.getOwned(id, user.userId()));
    }

    @GetMapping("/{id}/saga")
    public SagaStateResponse getSaga(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        var saga = orders.getSagaStateOwned(id, user.userId());
        return new SagaStateResponse(id, saga.orderStatus(), saga.steps());
    }

    public record SagaStateResponse(long orderId, String orderStatus,
                                    java.util.List<ru.otus.orderservice.repository.OrderRepository.SagaStep> steps) {}
}
