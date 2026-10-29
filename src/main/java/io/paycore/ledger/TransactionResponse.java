package io.paycore.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * A completed money movement. Deliberately omits balances: the sender must not learn the receiver's balance.
 */
public record TransactionResponse(UUID id, TransactionType type, String status, long amount, String currency,
                                  String description, UUID fromWalletId, UUID toWalletId, Instant createdAt) {

    static TransactionResponse of(LedgerTransaction transaction, UUID fromWalletId, UUID toWalletId) {
        return new TransactionResponse(transaction.getId(), transaction.getType(), "COMPLETED",
                transaction.getAmount(), transaction.getCurrency(), transaction.getDescription(),
                fromWalletId, toWalletId, transaction.getCreatedAt());
    }
}
