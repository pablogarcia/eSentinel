package bo.solid.fiscal.infrastructure.outbox;

import bo.solid.fiscal.application.outbox.OutboxMessage;
import bo.solid.fiscal.application.outbox.OutboxRecord;
import bo.solid.fiscal.application.outbox.port.OutboxStore;
import bo.solid.fiscal.application.outbox.port.OutboxWriter;
import bo.solid.fiscal.domain.shared.Uuid7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutbox implements OutboxWriter, OutboxStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcOutbox(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public UUID append(OutboxMessage m) {
        UUID id = Uuid7.next();
        String payload;
        try {
            payload = json.writeValueAsString(m.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload de outbox no serializable", e);
        }
        jdbc.sql("""
                INSERT INTO outbox_event (id, tenant_id, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)""")
                .params(id, m.tenantId(), m.aggregateType(), m.aggregateId(), m.eventType(), payload).update();
        return id;
    }

    @Override
    public List<OutboxRecord> claimBatch(int n) {
        // El relay opera entre tenants: asume el rol restringido SOLO durante esta transaccion.
        jdbc.sql("SET LOCAL ROLE fiscal_relay_role").update();
        return jdbc.sql("""
                SELECT id, tenant_id, aggregate_type, aggregate_id, event_type, payload::text AS payload, attempts
                FROM outbox_event
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY created_at
                LIMIT ? FOR UPDATE SKIP LOCKED""")
                .param(n)
                .query((rs, i) -> new OutboxRecord(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getString("aggregate_type"), rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"), rs.getString("payload"), rs.getInt("attempts")))
                .list();
    }

    @Override
    public void markPublished(UUID id) {
        jdbc.sql("UPDATE outbox_event SET status = 'PUBLISHED', published_at = now(), last_error = NULL WHERE id = ?")
                .param(id).update();
    }

    @Override
    public void markFailed(UUID id, int newAttempts, boolean dead, long delaySeconds, String error) {
        jdbc.sql("""
                UPDATE outbox_event SET attempts = ?, status = ?, last_error = ?,
                       next_attempt_at = now() + make_interval(secs => ?)
                WHERE id = ?""")
                .params(newAttempts, dead ? "DEAD" : "PENDING", error, (double) delaySeconds, id).update();
    }
}
