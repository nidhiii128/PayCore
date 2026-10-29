package io.paycore.wallet;

import io.paycore.common.ApiException;
import io.paycore.common.BaseEntity;
import io.paycore.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Balance is a cached projection of the wallet's ledger entries. It is only changed through
 * {@link #debit} and {@link #credit}, which the ledger calls while holding a row lock.
 */
@Entity
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    // Second line of defence: a stale write fails instead of silently losing an update.
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public Wallet(UUID accountId, String currency) {
        super(UUID.randomUUID());
        this.accountId = accountId;
        this.currency = currency;
        this.balance = 0;
        this.status = WalletStatus.ACTIVE;
        this.allowNegative = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void debit(long amount) {
        requirePositive(amount);
        if (!allowNegative && balance < amount) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds in wallet " + getId());
        }
        balance = Math.subtractExact(balance, amount);
    }

    public void credit(long amount) {
        requirePositive(amount);
        balance = Math.addExact(balance, amount);
    }

    public void freeze() {
        status = WalletStatus.FROZEN;
    }

    public void unfreeze() {
        status = WalletStatus.ACTIVE;
    }

    public boolean isFrozen() {
        return status == WalletStatus.FROZEN;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalance() {
        return balance;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
