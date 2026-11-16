package ru.otus.deliveryservice.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.otus.deliveryservice.repository.DeliveryRepository;
import ru.otus.deliveryservice.repository.DeliveryRepository.Reservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {
    private DeliveryRepository repository;
    private DeliveryService service;

    @BeforeEach
    void setUp() { repository=mock(DeliveryRepository.class); service=new DeliveryService(repository); }

    @Test
    void returnsSpecificCourierAndAllowsExactRetry() {
        when(repository.findReservation(51)).thenReturn(Optional.empty(),
                Optional.of(new Reservation(51, "courier-001", "slot-1")));
        when(repository.lockAvailableCourier("slot-1")).thenReturn(Optional.of("courier-001"));
        var expected = new Reservation(51, "courier-001", "slot-1");
        assertThat(service.reserve(51, "slot-1")).isEqualTo(expected);
        assertThat(service.reserve(51, "slot-1")).isEqualTo(expected);
        verify(repository).reserve(51, "courier-001", "slot-1");
    }

    @Test
    void conflictingRetryAndUnavailableCourierAreRejected() {
        when(repository.findReservation(52)).thenReturn(Optional.of(new Reservation(52, "courier-001", "slot-1")));
        assertThatThrownBy(() -> service.reserve(52, "slot-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("another slot");
        when(repository.findReservation(53)).thenReturn(Optional.empty());
        when(repository.lockAvailableCourier("slot-1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reserve(53, "slot-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No courier available");
    }
}
