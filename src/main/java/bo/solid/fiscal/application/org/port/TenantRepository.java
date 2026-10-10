package bo.solid.fiscal.application.org.port;

import bo.solid.fiscal.domain.org.Tenant;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    void insert(Tenant tenant);
    Optional<Tenant> findTenant(UUID id);
}
