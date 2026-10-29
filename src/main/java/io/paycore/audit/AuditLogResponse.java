package io.paycore.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(UUID id, AuditAction action, String entityType, UUID entityId,
                               Map<String, Object> details, String ipAddress, Instant createdAt) {

    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
