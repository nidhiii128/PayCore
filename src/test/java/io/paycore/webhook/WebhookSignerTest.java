package io.paycore.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private static final String SECRET = "whsec_test";
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"transfer.sent\"}";

    @Test
    void signatureVerifiesWithTheSameSecretAndBody() {
        String header = WebhookSigner.header(SECRET, 1_700_000_000L, BODY);

        assertThat(header).startsWith("t=1700000000,v1=");
        assertThat(WebhookSigner.verify(SECRET, header, BODY)).isTrue();
    }

    @Test
    void tamperingWithBodyTimestampOrSecretFailsVerification() {
        String header = WebhookSigner.header(SECRET, 1_700_000_000L, BODY);

        assertThat(WebhookSigner.verify(SECRET, header, BODY.replace("sent", "received"))).isFalse();
        assertThat(WebhookSigner.verify(SECRET, header.replace("t=1700000000", "t=1700000001"), BODY)).isFalse();
        assertThat(WebhookSigner.verify("whsec_other", header, BODY)).isFalse();
    }

    @Test
    void malformedHeadersAreRejected() {
        assertThat(WebhookSigner.verify(SECRET, null, BODY)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "", BODY)).isFalse();
        assertThat(WebhookSigner.verify(SECRET, "v1=abc", BODY)).isFalse();
    }
}
