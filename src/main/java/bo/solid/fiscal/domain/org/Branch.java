package bo.solid.fiscal.domain.org;

import bo.solid.fiscal.domain.shared.Uuid7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sucursal. codigoSucursal 0 = Casa Matriz, 1..n = sucursales (semantica del SIAT). */
public record Branch(UUID id, UUID tenantId, UUID companyId, int codigoSucursal, String nombre,
                     String direccion, String municipio, String telefono, long version, Instant createdAt) {

    public Branch {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(companyId);
        if (codigoSucursal < 0) {
            throw new IllegalArgumentException("codigoSucursal debe ser >= 0");
        }
        requireText(nombre, 200, "nombre");
        requireText(direccion, 500, "direccion");
        requireText(municipio, 200, "municipio");
    }

    public static Branch create(UUID tenantId, UUID companyId, int codigoSucursal, String nombre,
                                String direccion, String municipio, String telefono) {
        return new Branch(Uuid7.next(), tenantId, companyId, codigoSucursal, nombre, direccion, municipio,
                telefono, 0, Instant.now());
    }

    public boolean isHeadOffice() { return codigoSucursal == 0; }

    private static void requireText(String v, int max, String field) {
        if (v == null || v.isBlank() || v.length() > max) {
            throw new IllegalArgumentException(field + " invalido (1.." + max + " caracteres)");
        }
    }
}
