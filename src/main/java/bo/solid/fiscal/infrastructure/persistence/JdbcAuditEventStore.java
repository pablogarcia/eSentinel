package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.audit.port.AuditEventStore;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.audit.AuditDraft;
import bo.solid.fiscal.domain.audit.AuditEvent;
import bo.solid.fiscal.domain.audit.AuditHasher;
import bo.solid.fiscal.domain.shared.Uuid7;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEventStore implements AuditEventStore {

    private final JdbcClient jdbc;

    JdbcAuditEventStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditEvent append(AuditDraft d) {
        // Serializa la cadena de ESTE tenant hasta el fin de la transaccion (otros tenants no se bloquean).
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").param("audit:" + d.tenantId()).query().singleRow();
        record Last(long seq, String hash) {}
        Last last = jdbc.sql("SELECT seq, hash FROM audit_event WHERE tenant_id = ? ORDER BY seq DESC LIMIT 1")
                .param(d.tenantId()).query((rs, n) -> new Last(rs.getLong("seq"), rs.getString("hash"))).optional()
                .orElse(new Last(0, AuditHasher.GENESIS));
        long seq = last.seq() + 1;
        String prevHash = last.hash();
        AuditEvent event = AuditEvent.seal(Uuid7.next(), seq, prevHash, d);
        jdbc.sql("""
                INSERT INTO audit_event (id, tenant_id, seq, occurred_at, actor_id, actor_roles, operation,
                    entity_type, entity_id, document_id, old_state, new_state, siat_request_ref, siat_response_ref,
                    ip, correlation_id, trace_id, prev_hash, hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(event.id(), d.tenantId(), seq, Timestamp.from(d.occurredAt()), d.actorId(), d.actorRoles(),
                        d.operation(), d.entityType(), d.entityId(), d.documentId(), d.oldState(), d.newState(),
                        d.siatRequestRef(), d.siatResponseRef(), d.ip(), d.correlationId(), d.traceId(),
                        event.prevHash(), event.hash())
                .update();
        return event;
    }

    @Override
    public List<AuditEvent> findAfter(long afterSeq, int limit) {
        UUID tenant = TenantContext.require();
        return jdbc.sql("""
                SELECT id, seq, tenant_id, occurred_at, actor_id, actor_roles, operation, entity_type, entity_id,
                       document_id, old_state, new_state, siat_request_ref, siat_response_ref, ip, correlation_id,
                       trace_id, prev_hash, hash
                FROM audit_event WHERE tenant_id = ? AND seq > ? ORDER BY seq LIMIT ?""")
                .params(tenant, afterSeq, limit)
                .query((rs, n) -> new AuditEvent(rs.getObject("id", UUID.class), rs.getLong("seq"),
                        new AuditDraft(rs.getObject("tenant_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                                rs.getString("actor_id"), rs.getString("actor_roles"), rs.getString("operation"),
                                rs.getString("entity_type"), rs.getObject("entity_id", UUID.class),
                                rs.getObject("document_id", UUID.class), rs.getString("old_state"),
                                rs.getString("new_state"), rs.getString("siat_request_ref"),
                                rs.getString("siat_response_ref"), rs.getString("ip"),
                                rs.getString("correlation_id"), rs.getString("trace_id")),
                        rs.getString("prev_hash"), rs.getString("hash")))
                .list();
    }
}
