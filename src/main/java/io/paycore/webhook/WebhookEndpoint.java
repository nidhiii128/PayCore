package io.paycore.webhook;

import io.paycore.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String secret;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "event_types", nullable = false, columnDefinition = "text[]")
    private List<String> eventTypes;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookEndpoint() {
    }

    WebhookEndpoint(UUID ownerId, String url, String secret, List<String> eventTypes) {
        super(UUID.randomUUID());
        this.ownerId = ownerId;
        this.url = url;
        this.secret = secret;
        this.eventTypes = List.copyOf(eventTypes);
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
