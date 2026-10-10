package bo.solid.fiscal.domain.org;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Codigo Unico de Inicio de Sistemas. Vigencia 365 dias; renovable desde 5 dias antes del vencimiento. */
public record Cuis(UUID id, UUID tenantId, UUID companyId, UUID branchId, UUID posId, String codigo, Instant validUntil,
                   String status, Instant requestedAt) {

    public static final Duration RENEWAL_WINDOW = Duration.ofDays(5);

    public boolean isValidAt(Instant now) { return now.isBefore(validUntil); }

    /** true desde el 5.o dia anterior al vencimiento (regla oficial). */
    public boolean isInRenewalWindow(Instant now) { return !now.isBefore(validUntil.minus(RENEWAL_WINDOW)); }

    @Override
    public String toString() { return "Cuis[id=" + id + ", branchId=" + branchId + ", posId=" + posId + ", validUntil=" + validUntil + "]"; }
}
