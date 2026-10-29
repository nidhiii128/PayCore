package io.paycore.ledger;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body of a deposit or withdrawal. {@code amount} is in minor units of the wallet's currency. */
public record MoneyRequest(
        @NotNull @Positive @Max(MAX_AMOUNT) Long amount,
        @Size(max = 500) String description) {

    /** 10^15 minor units: far above any real single payment, far below overflow. */
    public static final long MAX_AMOUNT = 1_000_000_000_000_000L;
}
