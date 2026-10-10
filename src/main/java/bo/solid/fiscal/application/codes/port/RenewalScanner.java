package bo.solid.fiscal.application.codes.port;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Busca, entre todos los tenants, los alcances cuyo CUIS/CUFD falta o esta por vencer. Solo lectura (rol de jobs). */
public interface RenewalScanner {

    record Candidate(UUID tenantId, UUID companyId, UUID branchId, UUID posId) {}

    List<Candidate> findDue(Duration cufdLeadTime, int limit);
}
