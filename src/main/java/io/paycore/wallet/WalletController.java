package io.paycore.wallet;

import io.paycore.common.PageResponse;
import io.paycore.common.Principals;
import io.paycore.ledger.PaymentService;
import io.paycore.ledger.WalletEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Wallets")
public class WalletController {

    private final WalletService walletService;
    private final PaymentService payments;

    public WalletController(WalletService walletService, PaymentService payments) {
        this.walletService = walletService;
        this.payments = payments;
    }

    @PostMapping("/accounts/{accountId}/wallets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a wallet in a currency (one per currency per account)")
    public WalletResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                                 @Valid @RequestBody CreateWalletRequest request) {
        return walletService.create(Principals.userId(jwt), accountId, request);
    }

    @GetMapping("/accounts/{accountId}/wallets")
    @Operation(summary = "List an account's wallets")
    public List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return walletService.list(Principals.userId(jwt), accountId);
    }

    @GetMapping("/wallets/{walletId}")
    @Operation(summary = "Get a wallet and its current balance")
    public WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return walletService.get(Principals.userId(jwt), walletId);
    }

    @GetMapping("/wallets/{walletId}/transactions")
    @Operation(summary = "Wallet statement: ledger entries, newest first, with the running balance")
    public PageResponse<WalletEntryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return payments.history(Principals.userId(jwt), walletId, PageResponse.pageable(page, size));
    }

    @PostMapping("/wallets/{walletId}/freeze")
    @Operation(summary = "Block all money movement in and out of the wallet")
    public WalletResponse freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return walletService.freeze(Principals.userId(jwt), walletId);
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    @Operation(summary = "Allow money movement again")
    public WalletResponse unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return walletService.unfreeze(Principals.userId(jwt), walletId);
    }
}
