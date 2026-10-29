package io.paycore.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code amount} is in minor units of the source wallet's currency. */
public record TransferRequest(
        @NotNull UUID fromWalletId,
        @NotNull UUID toWalletId,
        @NotNull @Positive @Max(MoneyRequest.MAX_AMOUNT) Long amount,
        @Size(max = 500) String description) {
}
