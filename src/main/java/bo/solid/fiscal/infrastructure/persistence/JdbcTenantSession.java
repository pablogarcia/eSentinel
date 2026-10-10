package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.tenancy.TenantSession;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcTenantSession implements TenantSession {

    private final JdbcClient jdbc;

    JdbcTenantSession(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bind(UUID tenantId) {
        // is_local=true: vale solo para la transaccion actual.
        jdbc.sql("SELECT set_config('app.tenant_id', ?, true)").param(tenantId.toString()).query().singleRow();
    }
}
