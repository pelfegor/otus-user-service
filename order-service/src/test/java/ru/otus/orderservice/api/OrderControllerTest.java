package ru.otus.orderservice.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import ru.otus.common.security.AuthenticatedUser;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.service.OrderService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerTest {
    private final OrderService service = mock(OrderService.class);
    private final OrderController controller = new OrderController(service);
    private final AuthenticatedUser user = new AuthenticatedUser(7L, "buyer", "buyer@example.com");

    @Test
    void priceOnlyRequestKeepsLegacyAcceptedResponse() {
        var request = new CreateOrderRequest(null, null, null, new BigDecimal("5.00"));
        when(service.createLegacy(7L, "buyer@example.com", request.price())).thenReturn(order("PENDING", null));
        var response = controller.create(user, "Bearer token", request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo("PENDING");
        verify(service).createLegacy(7L, "buyer@example.com", request.price());
    }

    @Test
    void completeSagaRequestReturnsCreatedResponseWithCourier() {
        var request = new CreateOrderRequest("SKU-001", 2, "slot-1", new BigDecimal("5.00"));
        when(service.create(7L, "buyer@example.com", "token", "SKU-001", 2, "slot-1", request.price()))
                .thenReturn(order("CONFIRMED", "courier-001"));
        var response = controller.create(user, "Bearer token", request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().courierId()).isEqualTo("courier-001");
    }

    @Test
    void partialSagaRequestIsRejected() {
        var request = new CreateOrderRequest("SKU-001", null, null, new BigDecimal("5.00"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.create(user, "Bearer token", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OrderEntity order(String status, String courierId) {
        var now = OffsetDateTime.now();
        return new OrderEntity(10L, 7L, "buyer@example.com", "SKU-001", 2, "slot-1", courierId,
                new BigDecimal("5.00"), status, now, now);
    }
}
