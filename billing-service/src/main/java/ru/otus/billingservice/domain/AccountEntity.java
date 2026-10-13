package ru.otus.billingservice.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("accounts")
public record AccountEntity(@Id Long id, Long userId, BigDecimal balance,
                            OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
