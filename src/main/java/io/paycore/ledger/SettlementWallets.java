package io.paycore.ledger;

import io.paycore.account.Account;
import io.paycore.wallet.WalletRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Settlement wallets stand for money outside the platform: a deposit debits the settlement wallet and credits the
 * user, a withdrawal does the reverse. They may go negative, and across all wallets of a currency the balances
 * sum to zero.
 */
@Component
class SettlementWallets {

    private final JdbcTemplate jdbc;
    private final WalletRepository wallets;

    SettlementWallets(JdbcTemplate jdbc, WalletRepository wallets) {
        this.jdbc = jdbc;
        this.wallets = wallets;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    UUID idFor(String currency) {
        // Created on first use. ON CONFLICT makes concurrent first deposits in a new currency safe.
        jdbc.update("""
                insert into wallets (id, account_id, currency, balance, status, allow_negative, version, created_at, updated_at)
                values (?, ?, ?, 0, 'ACTIVE', true, 0, now(), now())
                on conflict (account_id, currency) do nothing
                """, UUID.randomUUID(), Account.SYSTEM_ACCOUNT_ID, currency);
        return wallets.findIdByAccountIdAndCurrency(Account.SYSTEM_ACCOUNT_ID, currency).orElseThrow();
    }
}
