package ru.otus.orderservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import ru.otus.common.outbox.OutboxStore;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.repository.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private OrderRepository repository;
    private OutboxStore outbox;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        outbox = mock(OutboxStore.class);
        service = new OrderService(repository, outbox);
    }

    @Test
    void createsPendingOrderAndOutboxEvent() {
        var order = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", new BigDecimal("5.00"))).thenReturn(order);
        assertThat(service.create(7L, "a@example.com", new BigDecimal("5.00"))).isEqualTo(order);
        verify(outbox).enqueue(eq("orders.created"), eq("12"), any());
    }

    @Test
    void rejectsInvalidPriceBeforeStoring() {
        assertThatThrownBy(() -> service.create(7L, "a@example.com", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).create(any(Long.class), any(), any());
    }

    @Test
    void transitionsToPaidOrFailedAndIgnoresRepeatedEvent() {
        when(repository.recordPaymentEvent("evt-1", 12L)).thenReturn(1);
        when(repository.findById(12L)).thenReturn(Optional.of(order(12L, 7L, "PENDING")));
        service.applyPayment("evt-1", 12L, 7L, new BigDecimal("5.00"), "PAID");
        verify(repository).applyPayment(12L, "PAID");

        when(repository.recordPaymentEvent("evt-2", 12L)).thenReturn(1);
        service.applyPayment("evt-2", 12L, 7L, new BigDecimal("5.00"), "PAYMENT_FAILED");
        verify(repository).applyPayment(12L, "PAYMENT_FAILED");

        when(repository.recordPaymentEvent("evt-1", 12L)).thenReturn(0);
        service.applyPayment("evt-1", 12L, 7L, new BigDecimal("5.00"), "PAID");
        verify(repository, org.mockito.Mockito.times(2)).applyPayment(eq(12L), any());
    }

    @Test
    void deniesReadingAnotherUsersOrder() {
        when(repository.findById(12L)).thenReturn(Optional.of(order(12L, 7L, "PENDING")));
        assertThatThrownBy(() -> service.getOwned(12L, 8L)).isInstanceOf(AccessDeniedException.class);
    }

    private static OrderEntity order(Long id, Long userId, String status) {
        var now = OffsetDateTime.now();
        return new OrderEntity(id, userId, "a@example.com", new BigDecimal("5.00"), status, now, now);
    }
}
