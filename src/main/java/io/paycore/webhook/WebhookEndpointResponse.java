package io.paycore.webhook;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code secret} is only populated in the response to creation; it is never shown again. */
public record WebhookEndpointResponse(UUID id, String url, List<String> eventTypes, boolean active, String secret,
                                      Instant createdAt) {

    static WebhookEndpointResponse from(WebhookEndpoint endpoint, boolean includeSecret) {
        return new WebhookEndpointResponse(endpoint.getId(), endpoint.getUrl(), endpoint.getEventTypes(),
                endpoint.isActive(), includeSecret ? endpoint.getSecret() : null, endpoint.getCreatedAt());
    }
}
