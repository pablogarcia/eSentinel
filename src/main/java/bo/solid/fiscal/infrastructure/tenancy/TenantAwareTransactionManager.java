package bo.solid.fiscal.infrastructure.tenancy;

import bo.solid.fiscal.application.tenancy.TenantContext;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Al comenzar cada transaccion fija app.tenant_id con set_config(..., is_local=true): el valor vive solo
 * dentro de la transaccion y nunca se filtra a la conexion del pool. Sin tenant se fija '' => RLS no
 * devuelve ninguna fila (fail-closed).
 */
public class TenantAwareTransactionManager extends DataSourceTransactionManager {

    public TenantAwareTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
        String tenant = TenantContext.current().map(UUID::toString).orElse("");
        try (PreparedStatement ps = holder.getConnection().prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            ps.setString(1, tenant);
            ps.execute();
        } catch (SQLException e) {
            throw new CannotCreateTransactionException("No se pudo fijar el tenant de la transaccion", e);
        }
    }
}
