package io.paycore.ledger;

import java.time.Instant;
import java.util.UUID;

/** One line of a wallet statement. */
public record WalletEntryResponse(UUID entryId, UUID transactionId, TransactionType type, EntryDirection direction,
                                  long amount, String currency, long balanceAfter, String description,
                                  Instant createdAt) {
}
