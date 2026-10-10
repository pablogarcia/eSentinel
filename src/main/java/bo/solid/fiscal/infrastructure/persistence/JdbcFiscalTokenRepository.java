package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.org.port.FiscalTokenRepository;
import bo.solid.fiscal.domain.org.FiscalToken;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFiscalTokenRepository implements FiscalTokenRepository {

    private final JdbcClient jdbc;

    JdbcFiscalTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replaceActive(FiscalToken t) {
        jdbc.sql("UPDATE fiscal_token SET status = 'REVOKED', revoked_at = now() WHERE company_id = ? AND status = 'ACTIVE'")
                .param(t.companyId()).update();
        jdbc.sql("""
                INSERT INTO fiscal_token (id, tenant_id, company_id, token_ciphertext, key_id, expires_at, status,
                                          created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)""")
                .params(t.id(), t.tenantId(), t.companyId(), t.ciphertext(), t.keyId(), Timestamp.from(t.expiresAt()),
                        Timestamp.from(t.createdAt()), t.createdBy())
                .update();
    }

    @Override
    public Optional<FiscalToken> findActive(UUID companyId) {
        return jdbc.sql("""
                SELECT id, tenant_id, company_id, token_ciphertext, key_id, expires_at, status, created_at, created_by
                FROM fiscal_token WHERE company_id = ? AND status = 'ACTIVE'""")
                .param(companyId)
                .query((rs, n) -> new FiscalToken(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getObject("company_id", UUID.class), rs.getString("token_ciphertext"), rs.getString("key_id"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("created_by")))
                .optional();
    }
}
