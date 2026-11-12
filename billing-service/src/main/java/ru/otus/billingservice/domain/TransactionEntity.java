package ru.otus.billingservice.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("transactions")
public record TransactionEntity(@Id Long id, Long accountId, Long orderId,
                                BigDecimal amount, String type, String status,
                                OffsetDateTime createdAt) {}
