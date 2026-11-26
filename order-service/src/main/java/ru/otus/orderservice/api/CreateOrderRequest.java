package ru.otus.orderservice.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @Size(max = 100) String sku,
        @jakarta.validation.constraints.Min(1) Integer quantity,
        @Size(max = 100) String deliverySlot,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
        BigDecimal price) {}
