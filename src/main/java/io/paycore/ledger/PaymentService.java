package io.paycore.ledger;

import io.paycore.audit.AuditAction;
import io.paycore.audit.AuditService;
import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import io.paycore.common.PageResponse;
import io.paycore.idempotency.IdempotencyService;
import io.paycore.idempotency.IdempotentResponse;
import io.paycore.ledger.LedgerService.Posting;
import io.paycore.wallet.WalletRepository;
import io.paycore.wallet.WalletService;
import io.paycore.webhook.WebhookEventType;
import io.paycore.webhook.WebhookService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money-moving use cases. Each runs in a single database transaction that covers the idempotency key, the ledger
 * postings, the audit row and the webhook outbox rows, so either all of them happen or none do.
 */
@Service
public class PaymentService {

    private final LedgerService ledger;
    private final SettlementWallets settlement;
    private final WalletService walletService;
    private final WalletRepository wallets;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final IdempotencyService idempotency;
    private final AuditService audit;
    private final WebhookService webhooks;

    public PaymentService(LedgerService ledger, SettlementWallets settlement, WalletService walletService,
                          WalletRepository wallets, LedgerTransactionRepository transactions,
                          LedgerEntryRepository entries, IdempotencyService idempotency, AuditService audit,
                          WebhookService webhooks) {
        this.ledger = ledger;
        this.settlement = settlement;
        this.walletService = walletService;
        this.wallets = wallets;
        this.transactions = transactions;
        this.entries = entries;
        this.idempotency = idempotency;
        this.audit = audit;
        this.webhooks = webhooks;
    }

    @Transactional
    public IdempotentResponse<TransactionResponse> transfer(UUID userId, String idempotencyKey, TransferRequest request) {
        return idempotency.execute(userId, idempotencyKey, "transfer", request, TransactionResponse.class, () -> {
            walletService.requireOwned(userId, request.fromWalletId());
            TransactionResponse result = ledger.post(new Posting(TransactionType.TRANSFER, request.fromWalletId(),
                    request.toWalletId(), request.amount(), request.description(), userId));

            audit.record(userId, AuditAction.TRANSFER_COMPLETED, "TRANSACTION", result.id(), details(result));
            webhooks.publish(userId, WebhookEventType.TRANSFER_SENT, result);
            wallets.findOwnerId(request.toWalletId())
                    .ifPresent(owner -> webhooks.publish(owner, WebhookEventType.TRANSFER_RECEIVED, result));
            return IdempotentResponse.created(result);
        });
    }

    @Transactional
    public IdempotentResponse<TransactionResponse> deposit(UUID userId, UUID walletId, String idempotencyKey,
                                                           MoneyRequest request) {
        return idempotency.execute(userId, idempotencyKey, "deposit:" + walletId, request, TransactionResponse.class, () -> {
            walletService.requireOwned(userId, walletId);
            UUID settlementWallet = settlement.idFor(currencyOf(walletId));
            TransactionResponse result = ledger.post(new Posting(TransactionType.DEPOSIT, settlementWallet, walletId,
                    request.amount(), request.description(), userId));

            audit.record(userId, AuditAction.DEPOSIT_COMPLETED, "TRANSACTION", result.id(), details(result));
            webhooks.publish(userId, WebhookEventType.DEPOSIT_COMPLETED, result);
            return IdempotentResponse.created(result);
        });
    }

    @Transactional
    public IdempotentResponse<TransactionResponse> withdraw(UUID userId, UUID walletId, String idempotencyKey,
                                                            MoneyRequest request) {
        return idempotency.execute(userId, idempotencyKey, "withdrawal:" + walletId, request, TransactionResponse.class, () -> {
            walletService.requireOwned(userId, walletId);
            UUID settlementWallet = settlement.idFor(currencyOf(walletId));
            TransactionResponse result = ledger.post(new Posting(TransactionType.WITHDRAWAL, walletId, settlementWallet,
                    request.amount(), request.description(), userId));

            audit.record(userId, AuditAction.WITHDRAWAL_COMPLETED, "TRANSACTION", result.id(), details(result));
            webhooks.publish(userId, WebhookEventType.WITHDRAWAL_COMPLETED, result);
            return IdempotentResponse.created(result);
        });
    }

    /** Visible to the owner of either side of the transaction. */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID userId, UUID transactionId) {
        LedgerTransaction transaction = transactions.findById(transactionId).orElseThrow(PaymentService::txNotFound);
        List<LedgerEntry> legs = entries.findByTransactionId(transactionId);
        boolean visible = legs.stream().anyMatch(e -> wallets.isOwnedBy(e.getWalletId(), userId));
        if (!visible) {
            throw txNotFound();
        }
        UUID from = legOf(legs, EntryDirection.DEBIT);
        UUID to = legOf(legs, EntryDirection.CREDIT);
        return TransactionResponse.of(transaction, from, to);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletEntryResponse> history(UUID userId, UUID walletId, Pageable pageable) {
        walletService.requireOwned(userId, walletId);
        return PageResponse.from(entries.findHistory(walletId, pageable));
    }

    private String currencyOf(UUID walletId) {
        return wallets.findCurrency(walletId).orElseThrow(WalletService::notFound);
    }

    private static UUID legOf(List<LedgerEntry> legs, EntryDirection direction) {
        return legs.stream().filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getWalletId).findFirst().orElseThrow();
    }

    private static Map<String, Object> details(TransactionResponse result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fromWalletId", result.fromWalletId().toString());
        details.put("toWalletId", result.toWalletId().toString());
        details.put("amount", result.amount());
        details.put("currency", result.currency());
        return details;
    }

    private static ApiException txNotFound() {
        return new ApiException(ErrorCode.TRANSACTION_NOT_FOUND, "Transaction not found");
    }
}
