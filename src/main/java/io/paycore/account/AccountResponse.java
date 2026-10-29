package io.paycore.account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String name, AccountType type, Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(), account.getType(), account.getCreatedAt());
    }
}
