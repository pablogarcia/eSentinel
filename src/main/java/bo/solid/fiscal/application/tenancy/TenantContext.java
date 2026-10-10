package bo.solid.fiscal.application.tenancy;

import bo.solid.fiscal.domain.shared.FiscalException;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant de la peticion/job actual. La capa de infraestructura lo lee al abrir cada transaccion
 * para activar Row Level Security. Siempre debe limpiarse en un finally (ver TenantContext.runAs).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) { CURRENT.set(tenantId); }

    public static void clear() { CURRENT.remove(); }

    public static Optional<UUID> current() { return Optional.ofNullable(CURRENT.get()); }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw FiscalException.unauthenticated("No hay tenant en el contexto de la peticion");
        }
        return id;
    }

    public static <T> T runAs(UUID tenantId, java.util.function.Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
