package io.paycore.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param lease how long a claimed delivery stays invisible to other dispatchers; if this instance crashes
 *              mid-delivery, the delivery becomes due again after the lease expires
 */
@ConfigurationProperties("app.webhooks")
public record WebhookProperties(
        boolean dispatcherEnabled,
        Duration pollInterval,
        int batchSize,
        int maxAttempts,
        Duration backoffBase,
        Duration backoffMax,
        Duration lease,
        Duration requestTimeout,
        boolean allowPrivateHosts) {

    /** Exponential backoff: base, 2×base, 4×base, ... capped at backoffMax. {@code attempt} starts at 1. */
    public Duration backoff(int attempt) {
        int exponent = Math.clamp(attempt - 1, 0, 30);
        Duration delay = backoffBase.multipliedBy(1L << exponent);
        return delay.compareTo(backoffMax) > 0 ? backoffMax : delay;
    }
}
