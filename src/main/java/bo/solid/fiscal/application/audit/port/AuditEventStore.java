package bo.solid.fiscal.application.audit.port;

import bo.solid.fiscal.domain.audit.AuditDraft;
import bo.solid.fiscal.domain.audit.AuditEvent;
import java.util.List;

public interface AuditEventStore {
    /** Encadena y persiste serializando por tenant. Debe ejecutarse dentro de la transaccion del cambio auditado. */
    AuditEvent append(AuditDraft draft);

    /** Eventos del tenant actual con seq > afterSeq, en orden. */
    List<AuditEvent> findAfter(long afterSeq, int limit);
}
