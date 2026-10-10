package bo.solid.fiscal.application.outbox;

import java.util.Map;
import java.util.UUID;

public record OutboxMessage(UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
                            Map<String, Object> payload) {}
