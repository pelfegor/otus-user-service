package ru.otus.deliveryservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.deliveryservice.repository.DeliveryRepository.Reservation;
import ru.otus.deliveryservice.service.DeliveryService;

@RestController
@RequestMapping("/delivery/reservations")
public class DeliveryController {
    private final DeliveryService delivery;
    public DeliveryController(DeliveryService delivery) { this.delivery = delivery; }

    @PostMapping
    public Reservation reserve(@RequestBody ReservationRequest request) {
        return delivery.reserve(request.orderId(), request.slot());
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> release(@PathVariable long orderId) {
        delivery.release(orderId);
        return ResponseEntity.noContent().build();
    }

    public record ReservationRequest(long orderId, String slot) {}
}
