package io.paycore.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.idempotency")
public record IdempotencyProperties(Duration retention) {
}
