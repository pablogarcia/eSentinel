package bo.solid.fiscal.application.codes.port;

import bo.solid.fiscal.domain.org.Cufd;
import bo.solid.fiscal.domain.org.Cuis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessCodeRepository {

    /**
     * Candado exclusivo por (sucursal, POS) hasta el fin de la transaccion (pg_advisory_xact_lock). Quien lo obtiene
     * debe RELEER el estado antes de pedir nada al SIAT: asi dos instancias no piden un codigo innecesario.
     */
    void lockScope(UUID branchId, UUID posId);

    Optional<Cuis> findActiveCuis(UUID branchId, UUID posId);

    /** Marca el ACTIVE previo como SUPERSEDED e inserta el nuevo. */
    void replaceActiveCuis(Cuis cuis);

    /** Solo devuelve un CUFD ACTIVE ligado al CUIS ACTIVE (un CUIS nuevo invalida los CUFD anteriores). */
    Optional<Cufd> findActiveCufd(UUID branchId, UUID posId);

    void replaceActiveCufd(Cufd cufd);

    List<Cufd> cufdHistory(UUID branchId, UUID posId, int limit);

    List<Cuis> cuisHistory(UUID branchId, UUID posId, int limit);
}
