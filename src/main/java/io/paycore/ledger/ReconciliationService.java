package io.paycore.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Checks the two invariants of the ledger:
 * <ol>
 *   <li>every wallet's cached balance equals the sum of its entries, and</li>
 *   <li>per currency, all wallet balances (settlement wallets included) sum to zero.</li>
 * </ol>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final JdbcTemplate jdbc;

    public ReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record WalletDrift(UUID walletId, long balance, long ledgerBalance) {
    }

    public record CurrencyImbalance(String currency, long total) {
    }

    public record Report(List<WalletDrift> walletDrifts, List<CurrencyImbalance> currencyImbalances) {
        public boolean isClean() {
            return walletDrifts.isEmpty() && currencyImbalances.isEmpty();
        }
    }

    // REPEATABLE READ gives both queries the same snapshot, so in-flight transfers cannot cause false alarms.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report run() {
        List<WalletDrift> drifts = jdbc.query("""
                        select w.id, w.balance, coalesce(e.ledger_balance, 0)
                        from wallets w
                        left join (
                            select wallet_id,
                                   sum(case when direction = 'CREDIT' then amount else -amount end) as ledger_balance
                            from ledger_entries group by wallet_id) e on e.wallet_id = w.id
                        where w.balance <> coalesce(e.ledger_balance, 0)
                        """,
                (rs, i) -> new WalletDrift(rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3)));
        List<CurrencyImbalance> imbalances = jdbc.query("""
                        select currency, sum(balance) from wallets group by currency having sum(balance) <> 0
                        """,
                (rs, i) -> new CurrencyImbalance(rs.getString(1), rs.getLong(2)));

        Report report = new Report(drifts, imbalances);
        if (report.isClean()) {
            log.info("Reconciliation clean");
        } else {
            log.error("Reconciliation FAILED: {}", report);
        }
        return report;
    }
}
