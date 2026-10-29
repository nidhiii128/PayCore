package io.paycore.account;

import io.paycore.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    /** Seeded by the V1 migration. Owns the settlement wallets. */
    public static final UUID SYSTEM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(UUID ownerId, String name, AccountType type) {
        super(UUID.randomUUID());
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.createdAt = Instant.now();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
