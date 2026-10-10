package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.idempotency.StoredResponse;
import bo.solid.fiscal.application.idempotency.port.IdempotencyStore;
import bo.solid.fiscal.application.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean tryBegin(String key, String requestHash) {
        UUID tenant = TenantContext.require();
        return jdbc.sql("""
                INSERT INTO idempotency_record (tenant_id, idem_key, request_hash, status)
                VALUES (?, ?, ?, 'IN_PROGRESS') ON CONFLICT (tenant_id, idem_key) DO NOTHING""")
                .params(tenant, key, requestHash).update() == 1;
    }

    @Override
    public Optional<Record> find(String key) {
        UUID tenant = TenantContext.require();
        return jdbc.sql("""
                SELECT request_hash, status, response_status, response_location, response_body
                FROM idempotency_record WHERE tenant_id = ? AND idem_key = ?""")
                .params(tenant, key)
                .query((rs, n) -> new Record(rs.getString("request_hash"), rs.getString("status"),
                        rs.getObject("response_status") == null ? null
                                : new StoredResponse(rs.getInt("response_status"), rs.getString("response_location"),
                                rs.getString("response_body"))))
                .optional();
    }

    @Override
    public void complete(String key, StoredResponse r) {
        UUID tenant = TenantContext.require();
        jdbc.sql("""
                UPDATE idempotency_record SET status = 'COMPLETED', response_status = ?, response_location = ?,
                       response_body = ?, completed_at = now()
                WHERE tenant_id = ? AND idem_key = ?""")
                .params(r.status(), r.location(), r.body(), tenant, key).update();
    }
}
