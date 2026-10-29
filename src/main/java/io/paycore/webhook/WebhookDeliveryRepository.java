package io.paycore.webhook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndEndpointId(UUID id, UUID endpointId);

    @Modifying
    @Query(value = """
            update webhook_deliveries
            set status = 'PENDING', attempts = 0, next_attempt_at = now(), last_error = null
            where id = :id and status = 'DEAD'
            """, nativeQuery = true)
    int requeueDead(@Param("id") UUID id);
}
