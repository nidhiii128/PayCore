package io.paycore.wallet;

import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private final Wallet wallet = new Wallet(UUID.randomUUID(), "USD");

    @Test
    void creditAndDebitAdjustTheBalance() {
        wallet.credit(1_000);
        wallet.debit(400);

        assertThat(wallet.getBalance()).isEqualTo(600);
    }

    @Test
    void debitBeyondTheBalanceIsRejected() {
        wallet.credit(100);

        assertThatThrownBy(() -> wallet.debit(101))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThatThrownBy(() -> wallet.credit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.debit(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowIsDetectedRatherThanWrappingAround() {
        wallet.credit(Long.MAX_VALUE);

        assertThatThrownBy(() -> wallet.credit(1)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void freezeAndUnfreeze() {
        wallet.freeze();
        assertThat(wallet.isFrozen()).isTrue();
        wallet.unfreeze();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }
}
