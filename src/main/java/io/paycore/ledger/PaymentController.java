package io.paycore.ledger;

import io.paycore.common.Principals;
import io.paycore.idempotency.IdempotentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments")
public class PaymentController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotent-Replayed";
    private static final String KEY_DOC = "Unique per logical operation. Retrying with the same key and body "
            + "returns the original result instead of moving money twice.";

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/transfers")
    @Operation(summary = "Move money between two wallets of the same currency")
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return toResponse(payments.transfer(Principals.userId(jwt), idempotencyKey, request));
    }

    @PostMapping("/wallets/{walletId}/deposits")
    @Operation(summary = "Fund a wallet from outside the platform (simulated inbound payment)")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return toResponse(payments.deposit(Principals.userId(jwt), walletId, idempotencyKey, request));
    }

    @PostMapping("/wallets/{walletId}/withdrawals")
    @Operation(summary = "Pay money out of a wallet to outside the platform (simulated payout)")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @Parameter(description = KEY_DOC) @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody MoneyRequest request) {
        return toResponse(payments.withdraw(Principals.userId(jwt), walletId, idempotencyKey, request));
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Get a transaction the current user is a party to")
    public TransactionResponse getTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transactionId) {
        return payments.getTransaction(Principals.userId(jwt), transactionId);
    }

    private static ResponseEntity<TransactionResponse> toResponse(IdempotentResponse<TransactionResponse> result) {
        return ResponseEntity.status(result.status())
                .header(REPLAYED, String.valueOf(result.replayed()))
                .body(result.body());
    }
}
