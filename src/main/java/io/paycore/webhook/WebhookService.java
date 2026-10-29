package io.paycore.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.paycore.audit.AuditAction;
import io.paycore.audit.AuditService;
import io.paycore.common.ApiException;
import io.paycore.common.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;
    private final WebhookUrlValidator urlValidator;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public WebhookService(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries,
                          WebhookUrlValidator urlValidator, AuditService audit, ObjectMapper mapper) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.urlValidator = urlValidator;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** The envelope every receiver gets. {@code id} is stable across retries so receivers can de-duplicate. */
    public record WebhookEvent(UUID id, String type, Instant createdAt, Object data) {
    }

    @Transactional
    public WebhookEndpointResponse register(UUID userId, CreateWebhookRequest request) {
        String url = urlValidator.validate(request.url());
        List<String> eventTypes = request.eventTypes().stream().distinct().toList();
        for (String type : eventTypes) {
            if (!WebhookEventType.ALL.contains(type)) {
                throw new ApiException(ErrorCode.INVALID_EVENT_TYPE,
                        "Unknown event type '" + type + "'. Supported: " + WebhookEventType.ALL);
            }
        }
        WebhookEndpoint endpoint = endpoints.save(new WebhookEndpoint(userId, url, newSecret(), eventTypes));
        audit.record(userId, AuditAction.WEBHOOK_REGISTERED, "WEBHOOK_ENDPOINT", endpoint.getId(),
                Map.of("url", url, "eventTypes", eventTypes));
        return WebhookEndpointResponse.from(endpoint, true);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list(UUID userId) {
        return endpoints.findByOwnerIdOrderByCreatedAt(userId).stream()
                .map(e -> WebhookEndpointResponse.from(e, false))
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID endpointId) {
        WebhookEndpoint endpoint = requireOwned(userId, endpointId);
        endpoints.delete(endpoint);
        audit.record(userId, AuditAction.WEBHOOK_DELETED, "WEBHOOK_ENDPOINT", endpointId, Map.of());
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryResponse> deliveries(UUID userId, UUID endpointId, int limit) {
        requireOwned(userId, endpointId);
        return deliveries.findByEndpointIdOrderByCreatedAtDesc(endpointId, PageRequest.of(0, Math.clamp(limit, 1, 100)))
                .stream().map(WebhookDeliveryResponse::from).toList();
    }

    @Transactional
    public void retry(UUID userId, UUID endpointId, UUID deliveryId) {
        requireOwned(userId, endpointId);
        deliveries.findByIdAndEndpointId(deliveryId, endpointId)
                .orElseThrow(() -> new ApiException(ErrorCode.DELIVERY_NOT_FOUND, "Delivery not found"));
        if (deliveries.requeueDead(deliveryId) == 0) {
            throw new ApiException(ErrorCode.DELIVERY_NOT_RETRYABLE, "Only DEAD deliveries can be retried");
        }
        audit.record(userId, AuditAction.WEBHOOK_DELIVERY_RETRIED, "WEBHOOK_DELIVERY", deliveryId, Map.of());
    }

    /**
     * Writes one outbox row per subscribed endpoint, in the caller's transaction: the event is recorded if and only
     * if the money movement commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(UUID ownerId, String eventType, Object data) {
        List<WebhookEndpoint> subscribed = endpoints.findSubscribed(ownerId, eventType);
        if (subscribed.isEmpty()) {
            return;
        }
        WebhookEvent event = new WebhookEvent(UUID.randomUUID(), eventType, Instant.now(), data);
        String payload;
        try {
            payload = mapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        deliveries.saveAll(subscribed.stream()
                .map(endpoint -> new WebhookDelivery(endpoint.getId(), event.id(), eventType, payload))
                .toList());
    }

    private WebhookEndpoint requireOwned(UUID userId, UUID endpointId) {
        return endpoints.findByIdAndOwnerId(endpointId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.WEBHOOK_NOT_FOUND, "Webhook endpoint not found"));
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
