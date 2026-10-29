package io.paycore.ledger;

import io.paycore.common.BaseEntity;
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
@Table(name = "ledger_transactions")
public class LedgerTransaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String description;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    LedgerTransaction(TransactionType type, long amount, String currency, String description, UUID initiatedBy) {
        super(UUID.randomUUID());
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.createdAt = Instant.now();
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
