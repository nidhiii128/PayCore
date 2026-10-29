package io.paycore.ledger;

import io.paycore.common.BaseEntity;
import io.paycore.wallet.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry extends BaseEntity {

    /** Assigned by the database at insert time; orders a wallet's entries in commit order. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryDirection direction;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    /** Must be called after the wallet's balance has been updated, so {@code balanceAfter} is correct. */
    static LedgerEntry of(LedgerTransaction transaction, Wallet wallet, EntryDirection direction) {
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID());
        entry.transactionId = transaction.getId();
        entry.walletId = wallet.getId();
        entry.direction = direction;
        entry.amount = transaction.getAmount();
        entry.currency = transaction.getCurrency();
        entry.balanceAfter = wallet.getBalance();
        entry.createdAt = transaction.getCreatedAt();
        return entry;
    }

    private LedgerEntry(UUID id) {
        super(id);
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
