package bo.solid.fiscal.domain.org;

import bo.solid.fiscal.domain.shared.Uuid7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Contribuyente (NIT) de un tenant. Un tenant puede tener varios NIT. */
public record Company(UUID id, UUID tenantId, Nit nit, String razonSocial, Environment environment,
                      Modality modality, String systemCode, String status, long version, Instant createdAt) {

    public Company {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(nit);
        Objects.requireNonNull(environment);
        Objects.requireNonNull(modality);
        if (razonSocial == null || razonSocial.isBlank() || razonSocial.length() > 200) {
            throw new IllegalArgumentException("Razon social invalida (1..200 caracteres)");
        }
    }

    public static Company register(UUID tenantId, Nit nit, String razonSocial, Environment environment,
                                   Modality modality, String systemCode) {
        return new Company(Uuid7.next(), tenantId, nit, razonSocial.strip(), environment, modality,
                systemCode, "ACTIVE", 0, Instant.now());
    }
}
