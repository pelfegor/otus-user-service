package ru.otus.inventoryservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.inventoryservice.repository.InventoryRepository.Reservation;
import ru.otus.inventoryservice.service.InventoryService;

@RestController
@RequestMapping("/inventory/reservations")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @PostMapping
    public Reservation reserve(@RequestBody Reservation request) {
        return inventory.reserve(request.orderId(), request.sku(), request.quantity());
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> release(@PathVariable long orderId) {
        inventory.release(orderId);
        return ResponseEntity.noContent().build();
    }
}
