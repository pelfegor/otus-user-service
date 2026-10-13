package ru.otus.billingservice.api;

import java.math.BigDecimal;
import ru.otus.billingservice.domain.AccountEntity;

public record AccountResponse(Long userId, BigDecimal balance) {
    public static AccountResponse from(AccountEntity account) {
        return new AccountResponse(account.userId(), account.balance());
    }
}
