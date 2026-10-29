package io.paycore.ledger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reconciliation", name = "enabled", havingValue = "true")
class ReconciliationJob {

    private final ReconciliationService reconciliation;

    ReconciliationJob(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "${app.reconciliation.cron}")
    void run() {
        reconciliation.run();
    }
}
