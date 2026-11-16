package ru.otus.billingservice.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import ru.otus.billingservice.service.PaymentResult;
import ru.otus.billingservice.service.BillingService;
import ru.otus.common.security.AuthenticatedUser;

@RestController
@RequestMapping("/billing")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) { this.billing = billing; }

    @GetMapping("/accounts/me")
    public AccountResponse getBalance(@AuthenticationPrincipal AuthenticatedUser user) {
        return AccountResponse.from(billing.getAccount(user.userId()));
    }

    @PostMapping("/accounts/me")
    public ResponseEntity<AccountResponse> ensureAccount(@AuthenticationPrincipal AuthenticatedUser user) {
        billing.createAccount(user.userId());
        return ResponseEntity.ok(AccountResponse.from(billing.getAccount(user.userId())));
    }

    @PostMapping("/accounts/me/deposit")
    public ResponseEntity<AccountResponse> deposit(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(AccountResponse.from(billing.deposit(user.userId(), request.amount())));
    }

    @PostMapping("/orders/{orderId}/charge")
    public ResponseEntity<PaymentResult> charge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long orderId,
                                @Valid @RequestBody DepositRequest request) {
        PaymentResult result = billing.charge(orderId, user.userId(), request.amount());
        return ResponseEntity.status(result.status() == ru.otus.billingservice.service.PaymentStatus.SUCCESS ? 200 : 402)
                .body(result);
    }

    @DeleteMapping("/orders/{orderId}/refund")
    public ResponseEntity<Void> refund(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable long orderId) {
        billing.refund(orderId, user.userId());
        return ResponseEntity.noContent().build();
    }
}
