package io.paycore.wallet;

import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id, UUID accountId, String currency, long balance, WalletStatus status,
                             Instant createdAt, Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getId(), wallet.getAccountId(), wallet.getCurrency(), wallet.getBalance(),
                wallet.getStatus(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }
}
