package io.paycore;

import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The database itself refuses to corrupt the ledger, even if application code has a bug. */
class LedgerInvariantsIT extends IntegrationTest {

    @Autowired
    TransactionTemplate transactions;

    @Test
    void ledgerEntriesCannotBeUpdatedOrDeleted() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");
        api.deposit(alice, wallet, 1_000);

        assertThatThrownBy(() -> jdbc.update("update ledger_entries set amount = 1 where wallet_id = ?", wallet))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from ledger_entries where wallet_id = ?", wallet))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_logs where actor_id = ?", alice.id()))
                .hasMessageContaining("append-only");
    }

    @Test
    void anUnbalancedTransactionIsRejectedAtCommit() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");
        UUID txId = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            jdbc.update("""
                    insert into ledger_transactions (id, type, amount, currency, created_at)
                    values (?, 'DEPOSIT', 500, 'USD', now())""", txId);
            // Only the credit leg: money created out of thin air.
            jdbc.update("""
                    insert into ledger_entries (id, transaction_id, wallet_id, direction, amount, currency, balance_after, created_at)
                    values (?, ?, ?, 'CREDIT', 500, 'USD', 500, now())""", UUID.randomUUID(), txId, wallet);
        })).rootCause().hasMessageContaining("unbalanced");

        Integer count = jdbc.queryForObject("select count(*) from ledger_transactions where id = ?", Integer.class, txId);
        assertThat(count).isZero();
    }

    @Test
    void walletBalanceCannotGoNegative() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");

        assertThatThrownBy(() -> jdbc.update("update wallets set balance = -1 where id = ?", wallet))
                .hasMessageContaining("wallets_balance_non_negative");
    }
}
