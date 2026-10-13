package ru.otus.notificationservice.service;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import ru.otus.notificationservice.repository.NotificationRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {
    @Test
    void storesBothPaymentOutcomesWithReadableMessages() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(repository);
        service.paymentSucceeded("success-event", 9L, 101L, "user@example.com", new BigDecimal("12.50"));
        service.paymentFailed("failed-event", 9L, 102L, "user@example.com", new BigDecimal("13.25"), "INSUFFICIENT_BALANCE");
        verify(repository).save("success-event", 9L, 101L, "user@example.com", new BigDecimal("12.50"),
                "Order payment succeeded", "Your order 101 for 12.50 has been paid successfully.");
        verify(repository).save("failed-event", 9L, 102L, "user@example.com", new BigDecimal("13.25"),
                "Order payment failed", "Your order 102 for 13.25 could not be paid: INSUFFICIENT_BALANCE.");
    }
}
