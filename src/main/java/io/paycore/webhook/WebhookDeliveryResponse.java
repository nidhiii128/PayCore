package io.paycore.webhook;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(UUID id, UUID eventId, String eventType, DeliveryStatus status, int attempts,
                                      Instant nextAttemptAt, Integer lastStatusCode, String lastError,
                                      Instant deliveredAt, Instant createdAt) {

    static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(d.getId(), d.getEventId(), d.getEventType(), d.getStatus(),
                d.getAttempts(), d.getNextAttemptAt(), d.getLastStatusCode(), d.getLastError(),
                d.getDeliveredAt(), d.getCreatedAt());
    }
}
