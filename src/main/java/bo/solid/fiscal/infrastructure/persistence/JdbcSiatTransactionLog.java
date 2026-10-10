package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.siat.port.SiatTransactionLog;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.shared.Uuid7;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cada metodo confirma en su PROPIA transaccion: la bitacora sobrevive aunque falle la operacion de negocio. */
@Repository
class JdbcSiatTransactionLog implements SiatTransactionLog {

    private final JdbcClient jdbc;

    JdbcSiatTransactionLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID begin(UUID companyId, String service, String operation, UUID documentId, String correlationId) {
        UUID id = Uuid7.next();
        jdbc.sql("""
                INSERT INTO siat_transaction (id, tenant_id, company_id, service, operation, document_id, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)""")
                .params(id, TenantContext.require(), companyId, service, operation, documentId, correlationId).update();
        return id;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID id, Outcome outcome, String siatCodes, String errorMessage, long latencyMs) {
        jdbc.sql("""
                UPDATE siat_transaction SET outcome = ?, siat_codes = ?, error_message = ?, latency_ms = ?,
                       finished_at = now()
                WHERE id = ? AND tenant_id = ? AND outcome = 'STARTED'""")
                .params(outcome.name(), siatCodes, errorMessage, (int) Math.min(latencyMs, Integer.MAX_VALUE), id,
                        TenantContext.require()).update();
    }
}
