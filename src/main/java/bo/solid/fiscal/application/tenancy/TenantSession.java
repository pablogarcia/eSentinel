package bo.solid.fiscal.application.tenancy;

import java.util.UUID;

/** Permite a una operacion de plataforma (alta de tenant) enlazar la TX actual al tenant recien creado. */
public interface TenantSession {
    void bind(UUID tenantId);
}
