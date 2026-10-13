package ru.otus.billingservice.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.otus.billingservice.service.BillingService;
import ru.otus.common.security.AuthenticatedUser;

@RestController
@RequestMapping("/billing/accounts/me")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) { this.billing = billing; }

    @GetMapping
    public AccountResponse getBalance(@AuthenticationPrincipal AuthenticatedUser user) {
        return AccountResponse.from(billing.getAccount(user.userId()));
    }

    @PostMapping("/deposit")
    public ResponseEntity<AccountResponse> deposit(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(AccountResponse.from(billing.deposit(user.userId(), request.amount())));
    }
}
