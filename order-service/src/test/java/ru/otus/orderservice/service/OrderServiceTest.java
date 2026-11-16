package ru.otus.orderservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import org.springframework.security.access.AccessDeniedException;
import ru.otus.common.outbox.OutboxStore;
import ru.otus.orderservice.domain.OrderEntity;
import ru.otus.orderservice.repository.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private OrderRepository repository;
    private OutboxStore outbox;
    private OrderService service;
    private MockRestServiceServer remoteServices;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        outbox = mock(OutboxStore.class);
        RestClient.Builder clients = RestClient.builder();
        remoteServices = MockRestServiceServer.bindTo(clients).build();
        service = new OrderService(repository, outbox, clients, "http://billing", "http://inventory", "http://delivery");
    }

    @Test
    void createsConfirmedOrderAfterAllParticipantsSucceed() {
        var pending = order(12L, 7L, "PENDING");
        var confirmed = order(12L, 7L, "CONFIRMED");
        when(repository.create(7L, "a@example.com", "SKU-1", 2, "slot-1", new BigDecimal("5.00"))).thenReturn(pending);
        when(repository.findById(12L)).thenReturn(Optional.of(confirmed));
        expectSuccess("http://billing/billing/orders/12/charge", HttpMethod.POST);
        expectSuccess("http://inventory/inventory/reservations", HttpMethod.POST);
        expectCourier("http://delivery/delivery/reservations");

        assertThat(service.create(7L, "a@example.com", "jwt", "SKU-1", 2, "slot-1", new BigDecimal("5.00")))
                .isEqualTo(confirmed);
        remoteServices.verify();
        verify(repository).setCourierId(12L, "courier-001");
        verify(repository).setStatus(12L, "CONFIRMED");
    }

    @Test
    void compensatesEarlierStepsWhenDeliveryReservationFails() {
        var pending = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", "SKU-1", 2, "slot-1", new BigDecimal("5.00"))).thenReturn(pending);
        expectSuccess("http://billing/billing/orders/12/charge", HttpMethod.POST);
        expectSuccess("http://inventory/inventory/reservations", HttpMethod.POST);
        remoteServices.expect(MockRestRequestMatchers.requestTo("http://delivery/delivery/reservations"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.CONFLICT));
        expectSuccess("http://delivery/delivery/reservations/12", HttpMethod.DELETE);
        expectSuccess("http://inventory/inventory/reservations/12", HttpMethod.DELETE);
        expectSuccess("http://billing/billing/orders/12/refund", HttpMethod.DELETE);

        assertThatThrownBy(() -> service.create(7L, "a@example.com", "jwt", "SKU-1", 2, "slot-1", new BigDecimal("5.00")))
                .isInstanceOf(SagaFailedException.class)
                .hasMessageContaining("completed steps were compensated");
        remoteServices.verify();
        verify(repository).setStatus(12L, "FAILED");
    }

    @Test
    void compensatesChargeWhenInventoryReservationFails() {
        var pending = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", "SKU-1", 2, "slot-1", new BigDecimal("5.00"))).thenReturn(pending);
        expectSuccess("http://billing/billing/orders/12/charge", HttpMethod.POST);
        remoteServices.expect(MockRestRequestMatchers.requestTo("http://inventory/inventory/reservations"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.CONFLICT));
        expectSuccess("http://inventory/inventory/reservations/12", HttpMethod.DELETE);
        expectSuccess("http://billing/billing/orders/12/refund", HttpMethod.DELETE);

        assertThatThrownBy(() -> service.create(7L, "a@example.com", "jwt", "SKU-1", 2, "slot-1", new BigDecimal("5.00")))
                .isInstanceOf(SagaFailedException.class)
                .hasMessageContaining("completed steps were compensated");
        remoteServices.verify();
        verify(repository).recordSagaStep(12L, "INVENTORY", "COMPENSATED", null);
        verify(repository).recordSagaStep(12L, "BILLING", "COMPENSATED", null);
        verify(repository).setStatus(12L, "FAILED");
    }

    @Test
    void failedChargeDoesNotCallLaterParticipantsAndRefundIsSafeNoop() {
        var pending = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", "SKU-1", 2, "slot-1", new BigDecimal("5.00"))).thenReturn(pending);
        remoteServices.expect(MockRestRequestMatchers.requestTo("http://billing/billing/orders/12/charge"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.PAYMENT_REQUIRED));
        expectSuccess("http://billing/billing/orders/12/refund", HttpMethod.DELETE);

        assertThatThrownBy(() -> service.create(7L, "a@example.com", "jwt", "SKU-1", 2, "slot-1", new BigDecimal("5.00")))
                .isInstanceOf(SagaFailedException.class);
        remoteServices.verify();
        verify(repository).setStatus(12L, "FAILED");
    }

    @Test
    void recordsCompensationFailureForReconciliation() {
        var pending = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", "SKU-1", 2, "slot-1", new BigDecimal("5.00"))).thenReturn(pending);
        expectSuccess("http://billing/billing/orders/12/charge", HttpMethod.POST);
        remoteServices.expect(MockRestRequestMatchers.requestTo("http://inventory/inventory/reservations"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.CONFLICT));
        for (int attempt = 0; attempt < 3; attempt++) {
            remoteServices.expect(MockRestRequestMatchers.requestTo("http://inventory/inventory/reservations/12"))
                    .andExpect(MockRestRequestMatchers.method(HttpMethod.DELETE))
                    .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        }
        expectSuccess("http://billing/billing/orders/12/refund", HttpMethod.DELETE);

        assertThatThrownBy(() -> service.create(7L, "a@example.com", "jwt", "SKU-1", 2, "slot-1", new BigDecimal("5.00")))
                .isInstanceOf(SagaFailedException.class)
                .hasMessageContaining("compensation needs attention");
        remoteServices.verify();
        verify(repository).setStatus(12L, "COMPENSATION_REQUIRED");
    }

    @Test
    void rejectsInvalidPriceBeforeStoring() {
        assertThatThrownBy(() -> service.create(7L, "a@example.com", "jwt", "SKU-1", 1, "slot-1", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).create(anyLong(), anyString(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void legacyCreateStillPublishesOrderEvent() {
        var pending = order(12L, 7L, "PENDING");
        when(repository.create(7L, "a@example.com", "legacy", 1, "legacy", new BigDecimal("5.00")))
                .thenReturn(pending);

        assertThat(service.createLegacy(7L, "a@example.com", new BigDecimal("5.00"))).isEqualTo(pending);
        verify(outbox).enqueue(eq("orders.created"), eq("12"), any());
    }

    @Test
    void deniesReadingAnotherUsersOrder() {
        when(repository.findById(12L)).thenReturn(Optional.of(order(12L, 7L, "PENDING")));
        assertThatThrownBy(() -> service.getOwned(12L, 8L)).isInstanceOf(AccessDeniedException.class);
    }

    private void expectSuccess(String url, HttpMethod method) {
        remoteServices.expect(MockRestRequestMatchers.requestTo(url))
                .andExpect(MockRestRequestMatchers.method(method))
                .andRespond(MockRestResponseCreators.withSuccess());
    }

    private void expectCourier(String url) {
        remoteServices.expect(MockRestRequestMatchers.requestTo(url))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withSuccess(
                        "{\"orderId\":12,\"courierId\":\"courier-001\",\"slot\":\"slot-1\"}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
    }

    private static OrderEntity order(Long id, Long userId, String status) {
        var now = OffsetDateTime.now();
        return new OrderEntity(id, userId, "a@example.com", "SKU-1", 2, "slot-1", null,
                new BigDecimal("5.00"), status, now, now);
    }
}
