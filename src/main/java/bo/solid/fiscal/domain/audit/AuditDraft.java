package bo.solid.fiscal.domain.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** Datos de un evento de auditoria antes de entrar en la cadena (sin seq ni hashes). */
public record AuditDraft(UUID tenantId, Instant occurredAt, String actorId, String actorRoles, String operation,
                         String entityType, UUID entityId, UUID documentId, String oldState, String newState,
                         String siatRequestRef, String siatResponseRef, String ip, String correlationId,
                         String traceId) {

    public AuditDraft {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(entityType);
        // PostgreSQL guarda microsegundos: truncar aqui hace que el hash sea reproducible tras releer.
        occurredAt = Objects.requireNonNull(occurredAt).truncatedTo(ChronoUnit.MICROS);
        actorRoles = actorRoles == null ? "" : actorRoles;
    }
}
