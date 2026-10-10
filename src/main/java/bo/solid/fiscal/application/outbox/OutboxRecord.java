package bo.solid.fiscal.application.outbox;

import java.util.UUID;

public record OutboxRecord(UUID id, UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
                           String payloadJson, int attempts) {}
