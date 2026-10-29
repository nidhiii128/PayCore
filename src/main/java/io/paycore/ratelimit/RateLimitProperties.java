package io.paycore.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param anonymousLimit applies per client IP to unauthenticated endpoints (login, register), to slow down
 *                       credential stuffing
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, Duration window, int authenticatedLimit, int anonymousLimit) {
}
