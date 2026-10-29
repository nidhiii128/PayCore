package io.paycore.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    Optional<WebhookEndpoint> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query(value = """
            select * from webhook_endpoints
            where owner_id = :ownerId and active and :eventType = any (event_types)
            """, nativeQuery = true)
    List<WebhookEndpoint> findSubscribed(@Param("ownerId") UUID ownerId, @Param("eventType") String eventType);
}
