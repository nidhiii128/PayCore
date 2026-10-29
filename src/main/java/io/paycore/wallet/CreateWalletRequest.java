package io.paycore.wallet;

import jakarta.validation.constraints.NotBlank;

public record CreateWalletRequest(@NotBlank String currency) {
}
