package bo.solid.fiscal.domain.org;

import bo.solid.fiscal.domain.shared.Uuid7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Punto de venta. codigoPuntoVenta >= 1 (el 0 significa "no corresponde" en el SIAT y no es un POS). */
public record PointOfSale(UUID id, UUID tenantId, UUID branchId, int codigoPuntoVenta, String nombre,
                          Integer tipoPuntoVenta, long version, Instant createdAt) {

    public PointOfSale {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(branchId);
        if (codigoPuntoVenta < 1) {
            throw new IllegalArgumentException("codigoPuntoVenta debe ser >= 1");
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > 200) {
            throw new IllegalArgumentException("nombre invalido (1..200 caracteres)");
        }
    }

    public static PointOfSale create(UUID tenantId, UUID branchId, int codigoPuntoVenta, String nombre,
                                     Integer tipoPuntoVenta) {
        return new PointOfSale(Uuid7.next(), tenantId, branchId, codigoPuntoVenta, nombre, tipoPuntoVenta,
                0, Instant.now());
    }
}
