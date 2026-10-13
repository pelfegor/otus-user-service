package ru.otus.orderservice.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                                                 @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = OrderResponse.from(orders.create(user.userId(), user.email(), request.price()));
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long id) {
        return OrderResponse.from(orders.getOwned(id, user.userId()));
    }
}
