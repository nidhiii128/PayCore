package io.paycore.wallet;

import io.paycore.account.AccountService;
import io.paycore.audit.AuditAction;
import io.paycore.audit.AuditService;
import io.paycore.common.ApiException;
import io.paycore.common.Currencies;
import io.paycore.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final AccountService accounts;
    private final AuditService audit;

    public WalletService(WalletRepository wallets, AccountService accounts, AuditService audit) {
        this.wallets = wallets;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public WalletResponse create(UUID userId, UUID accountId, CreateWalletRequest request) {
        accounts.requireOwned(userId, accountId);
        String currency = Currencies.normalize(request.currency());
        if (wallets.existsByAccountIdAndCurrency(accountId, currency)) {
            throw new ApiException(ErrorCode.WALLET_ALREADY_EXISTS, "Account already has a " + currency + " wallet");
        }
        Wallet wallet = wallets.save(new Wallet(accountId, currency));
        audit.record(userId, AuditAction.WALLET_CREATED, "WALLET", wallet.getId(),
                Map.of("accountId", accountId.toString(), "currency", currency));
        return WalletResponse.from(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> list(UUID userId, UUID accountId) {
        accounts.requireOwned(userId, accountId);
        return wallets.findByAccountIdOrderByCreatedAt(accountId).stream().map(WalletResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse get(UUID userId, UUID walletId) {
        return wallets.findOwned(walletId, userId)
                .map(WalletResponse::from)
                .orElseThrow(WalletService::notFound);
    }

    @Transactional
    public WalletResponse freeze(UUID userId, UUID walletId) {
        Wallet wallet = lockOwned(userId, walletId);
        wallet.freeze();
        audit.record(userId, AuditAction.WALLET_FROZEN, "WALLET", walletId, Map.of());
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse unfreeze(UUID userId, UUID walletId) {
        Wallet wallet = lockOwned(userId, walletId);
        wallet.unfreeze();
        audit.record(userId, AuditAction.WALLET_UNFROZEN, "WALLET", walletId, Map.of());
        return WalletResponse.from(wallet);
    }

    /** Throws 404 unless the wallet exists and belongs to the user. Does not load the entity. */
    @Transactional(readOnly = true)
    public void requireOwned(UUID userId, UUID walletId) {
        if (!wallets.isOwnedBy(walletId, userId)) {
            throw notFound();
        }
    }

    private Wallet lockOwned(UUID userId, UUID walletId) {
        requireOwned(userId, walletId);
        return wallets.lockAll(List.of(walletId)).getFirst();
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found");
    }
}
