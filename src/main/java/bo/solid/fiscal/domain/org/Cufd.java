package bo.solid.fiscal.domain.org;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Codigo Unico de Facturacion Diaria (24 h). codigoControl entra en el CUF. */
public record Cufd(UUID id, UUID tenantId, UUID companyId, UUID branchId, UUID posId, UUID cuisId, String codigo,
                   String codigoControl, String direccion, Instant validUntil, String status, Instant requestedAt) {

    public boolean isValidAt(Instant now) { return now.isBefore(validUntil); }

    /** Quedan menos de {@code lead} para que venza (o ya vencio): conviene renovarlo. */
    public boolean needsRenewal(Instant now, Duration lead) { return !now.isBefore(validUntil.minus(lead)); }

    @Override
    public String toString() { return "Cufd[id=" + id + ", branchId=" + branchId + ", posId=" + posId + ", validUntil=" + validUntil + "]"; }
}
