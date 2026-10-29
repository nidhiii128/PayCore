package io.paycore.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.webhooks", name = "dispatcher-enabled", havingValue = "true")
class WebhookScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebhookScheduler.class);

    private final WebhookDispatcher dispatcher;

    WebhookScheduler(WebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.webhooks.poll-interval}")
    void run() {
        try {
            // Drain the backlog, then wait for the next tick.
            int dispatched;
            do {
                dispatched = dispatcher.dispatchDue();
            } while (dispatched > 0);
        } catch (RuntimeException e) {
            log.error("Webhook dispatch failed", e);
        }
    }
}
