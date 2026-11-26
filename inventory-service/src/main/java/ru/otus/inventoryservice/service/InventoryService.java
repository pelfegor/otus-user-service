package ru.otus.inventoryservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.otus.inventoryservice.repository.InventoryRepository;
import ru.otus.inventoryservice.repository.InventoryRepository.Reservation;

@Service
public class InventoryService {
    private final InventoryRepository inventory;
    public InventoryService(InventoryRepository inventory) { this.inventory = inventory; }

    @Transactional
    public Reservation reserve(long orderId, String sku, int quantity) {
        if (orderId <= 0 || quantity <= 0 || sku == null || sku.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reservation");
        inventory.lockOrder(orderId);
        var existing = inventory.findReservation(orderId);
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (reservation.sku().equals(sku) && reservation.quantity() == quantity) return reservation;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "orderId already reserved with different SKU or quantity");
        }
        int available = inventory.lockAvailable(sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Unknown SKU"));
        existing = inventory.findReservation(orderId);
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (reservation.sku().equals(sku) && reservation.quantity() == quantity) return reservation;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "orderId already reserved with different SKU or quantity");
        }
        if (available < quantity) throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
        inventory.reserve(orderId, sku, quantity);
        return new Reservation(orderId, sku, quantity);
    }

    @Transactional
    public void release(long orderId) {
        inventory.lockOrder(orderId);
        inventory.release(orderId).ifPresent(r -> inventory.restoreStock(r.sku(), r.quantity()));
    }
}
