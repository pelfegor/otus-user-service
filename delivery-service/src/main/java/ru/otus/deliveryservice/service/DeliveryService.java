package ru.otus.deliveryservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.otus.deliveryservice.repository.DeliveryRepository;
import ru.otus.deliveryservice.repository.DeliveryRepository.Reservation;

@Service
public class DeliveryService {
    private final DeliveryRepository delivery;
    public DeliveryService(DeliveryRepository delivery) { this.delivery = delivery; }

    @Transactional
    public Reservation reserve(long orderId, String slot) {
        if (orderId <= 0 || slot == null || slot.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid slot reservation");
        delivery.lockOrder(orderId);
        var existing = delivery.findReservation(orderId);
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (reservation.slot().equals(slot)) return reservation;
            throw new ResponseStatusException(HttpStatus.CONFLICT, "orderId already reserved for another slot");
        }
        String courierId = delivery.lockAvailableCourier(slot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No courier available for this slot"));
        delivery.reserve(orderId, courierId, slot);
        return new Reservation(orderId, courierId, slot);
    }

    @Transactional
    public void release(long orderId) {
        delivery.lockOrder(orderId);
        delivery.release(orderId);
    }
}
