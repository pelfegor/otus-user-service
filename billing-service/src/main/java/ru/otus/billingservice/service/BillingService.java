package ru.otus.billingservice.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.otus.billingservice.domain.AccountEntity;
import ru.otus.billingservice.domain.TransactionEntity;
import ru.otus.billingservice.repository.AccountRepository;
import ru.otus.billingservice.repository.TransactionRepository;
import ru.otus.common.outbox.OutboxStore;
import ru.otus.contracts.OrderCreated;
import ru.otus.contracts.PaymentFailed;
import ru.otus.contracts.PaymentSucceeded;

@Service
public class BillingService {
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final OutboxStore outbox;

    public BillingService(AccountRepository accounts, TransactionRepository transactions, OutboxStore outbox) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.outbox = outbox;
    }

    @Transactional
    public void createAccount(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId must be positive");
        accounts.createIfAbsent(userId);
    }

    @Transactional(readOnly = true)
    public AccountEntity getAccount(long userId) {
        return accounts.findByUserId(userId)
            .orElseThrow(() -> new AccountNotFoundException(userId));
    }

    @Transactional
    public AccountEntity deposit(long userId, BigDecimal amount) {
        validateAmount(amount);
        AccountEntity account = accounts.lockByUserId(userId)
            .orElseThrow(() -> new AccountNotFoundException(userId));
        accounts.deposit(account.id(), amount);
        transactions.saveDeposit(account.id(), amount);
        return getAccount(userId);
    }

    @Transactional
    public PaymentResult charge(long orderId, long userId, BigDecimal amount) {
        return charge(orderId, userId, null, amount);
    }

    @Transactional
    public PaymentResult processOrder(OrderCreated order) {
        Objects.requireNonNull(order, "order");
        if (order.orderId() == null || order.userId() == null) throw new IllegalArgumentException("IDs must be present");
        return charge(order.orderId(), order.userId(), order.email(), order.amount());
    }

    @Transactional
    public void failOrderForMissingAccount(OrderCreated order) {
        Objects.requireNonNull(order, "order");
        if (order.eventId() == null || order.orderId() == null || order.userId() == null || order.amount() == null) {
            throw new IllegalArgumentException("Order event is incomplete");
        }
        String eventId = UUID.nameUUIDFromBytes(("ACCOUNT_NOT_FOUND:" + order.eventId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        outbox.enqueue("payments.failed", Long.toString(order.orderId()),
                new PaymentFailed(eventId, Instant.now(), order.orderId(), order.userId(), order.email(),
                        order.amount(), "ACCOUNT_NOT_FOUND"));
    }

    private PaymentResult charge(long orderId, long userId, String email, BigDecimal amount) {
        if (orderId <= 0 || userId <= 0) throw new IllegalArgumentException("IDs must be positive");
        validateAmount(amount);
        // Serializes concurrent orders for the same account.
        AccountEntity account = accounts.lockByUserId(userId)
            .orElseThrow(() -> new AccountNotFoundException(userId));
        var previous = transactions.findByOrderId(orderId);
        if (previous.isPresent()) {
            TransactionEntity tx = previous.get();
            if (!Objects.equals(tx.accountId(), account.id()) || tx.amount().compareTo(amount) != 0) {
                throw new IllegalStateException("orderId already used with different payment data");
            }
            return new PaymentResult(orderId, userId, email, amount,
                PaymentStatus.valueOf(tx.status()), true,
                "FAILED".equals(tx.status()) ? "INSUFFICIENT_BALANCE" : null);
        }
        boolean succeeded = accounts.withdrawIfEnough(account.id(), amount) == 1;
        try {
            transactions.savePayment(account.id(), orderId, amount, succeeded);
        } catch (DuplicateKeyException race) {
            // The account lock serializes charges for one user; this also protects against
            // duplicate order delivery from a second database transaction.
            throw race;
        }
        String reason = succeeded ? null : "INSUFFICIENT_BALANCE";
        PaymentResult result = new PaymentResult(orderId, userId, email, amount,
                succeeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED, false, reason);
        String eventId = UUID.randomUUID().toString();
        if (succeeded) {
            outbox.enqueue("payments.succeeded", Long.toString(orderId),
                    new PaymentSucceeded(eventId, Instant.now(), orderId, userId, email, amount));
        } else {
            outbox.enqueue("payments.failed", Long.toString(orderId),
                    new PaymentFailed(eventId, Instant.now(), orderId, userId, email, amount, reason));
        }
        return result;
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 ||
            amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("amount must be positive with up to 2 decimal places and fit NUMERIC(19,2)");
        }
    }
}
