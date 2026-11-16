package ru.otus.inventoryservice.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.otus.inventoryservice.repository.InventoryRepository;
import ru.otus.inventoryservice.repository.InventoryRepository.Reservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {
    private InventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() { repository=mock(InventoryRepository.class); service=new InventoryService(repository); }

    @Test
    void exactRetryReturnsExistingReservationWithoutDecrementingAgain() {
        var existing = new Reservation(44, "SKU-001", 3);
        when(repository.findReservation(44)).thenReturn(Optional.of(existing));
        assertThat(service.reserve(44, "SKU-001", 3)).isEqualTo(existing);
        verify(repository).lockOrder(44);
    }

    @Test
    void retryWithDifferentSkuOrQuantityConflicts() {
        when(repository.findReservation(44)).thenReturn(Optional.of(new Reservation(44, "SKU-001", 3)));
        assertThatThrownBy(() -> service.reserve(44, "SKU-001", 4))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different SKU or quantity");
    }

    @Test
    void reservesAvailableStock() {
        when(repository.findReservation(45)).thenReturn(Optional.empty());
        when(repository.lockAvailable("SKU-001")).thenReturn(Optional.of(8));
        assertThat(service.reserve(45, "SKU-001", 2)).isEqualTo(new Reservation(45, "SKU-001", 2));
        verify(repository).reserve(45, "SKU-001", 2);
    }
}
