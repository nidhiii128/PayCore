package io.paycore.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookBackoffTest {

    private final WebhookProperties properties = new WebhookProperties(true, Duration.ofSeconds(2), 50, 8,
            Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(5), false);

    @Test
    void delayDoublesWithEachAttempt() {
        assertThat(properties.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.backoff(3)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void delayIsCappedAndNeverOverflows() {
        assertThat(properties.backoff(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.backoff(1_000)).isEqualTo(Duration.ofMinutes(5));
    }
}
