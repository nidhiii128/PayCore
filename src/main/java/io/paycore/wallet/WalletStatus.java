package io.paycore.wallet;

public enum WalletStatus {
    ACTIVE,
    /** Cannot send or receive funds until unfrozen. */
    FROZEN
}
