package ru.otus.billingservice.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.otus.billingservice.domain.AccountEntity;
import ru.otus.billingservice.domain.TransactionEntity;
import ru.otus.billingservice.repository.AccountRepository;
import ru.otus.billingservice.repository.TransactionRepository;
import ru.otus.common.outbox.OutboxStore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingRefundTest {
    private AccountRepository accounts;
    private TransactionRepository transactions;
    private BillingService billing;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        transactions = mock(TransactionRepository.class);
        billing = new BillingService(accounts, transactions, mock(OutboxStore.class));
    }

    @Test
    void refundsCommittedChargeOnlyOnce() {
        var now = OffsetDateTime.now();
        var tx = new TransactionEntity(1L, 10L, 51L, new BigDecimal("4.00"), "WITHDRAWAL", "SUCCESS", now);
        var account = new AccountEntity(10L, 7L, new BigDecimal("6.00"), now, now);
        when(transactions.findByOrderId(51L)).thenReturn(Optional.of(tx));
        when(accounts.lockByUserId(7L)).thenReturn(Optional.of(account));
        when(transactions.markRefunded(51L)).thenReturn(1, 0);

        billing.refund(51L, 7L);
        billing.refund(51L, 7L);

        verify(accounts).deposit(10L, new BigDecimal("4.00"));
    }

    @Test
    void missingChargeIsANoop() {
        when(transactions.findByOrderId(52L)).thenReturn(Optional.empty());
        billing.refund(52L, 7L);
        verify(accounts, never()).lockByUserId(7L);
    }
}
