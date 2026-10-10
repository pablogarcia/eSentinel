package bo.solid.fiscal.application.audit;

import bo.solid.fiscal.application.audit.port.AuditEventStore;
import bo.solid.fiscal.application.context.RequestContext;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.audit.AuditDraft;
import bo.solid.fiscal.domain.audit.AuditEvent;
import bo.solid.fiscal.domain.audit.AuditHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    public record Verification(boolean valid, long eventsChecked, Long firstBrokenSeq, String reason) {}

    private static final int BATCH = 1000;

    private final AuditEventStore store;

    public AuditService(AuditEventStore store) {
        this.store = store;
    }

    /** Registra un evento con el actor/ip/correlacion del contexto. MANDATORY: siempre dentro de la TX del cambio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(UUID tenantId, String operation, String entityType, UUID entityId, UUID documentId,
                             String oldState, String newState) {
        return recordWithSiat(tenantId, operation, entityType, entityId, documentId, oldState, newState, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent recordWithSiat(UUID tenantId, String operation, String entityType, UUID entityId,
                                     UUID documentId, String oldState, String newState, String siatRequestRef,
                                     String siatResponseRef) {
        RequestContext.Info ctx = RequestContext.currentOrSystem();
        List<String> roles = new ArrayList<>(new TreeSet<>(ctx.roles()));
        AuditDraft draft = new AuditDraft(tenantId, Instant.now(), ctx.actorId(), String.join(",", roles), operation,
                entityType, entityId, documentId, oldState, newState, siatRequestRef, siatResponseRef, ctx.ip(),
                ctx.correlationId(), ctx.traceId());
        return store.append(draft);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> list(long afterSeq, int limit) {
        TenantContext.require();
        return store.findAfter(afterSeq, Math.min(Math.max(limit, 1), 500));
    }

    /** Recalcula la cadena completa del tenant actual y detecta la primera alteracion. */
    @Transactional(readOnly = true)
    public Verification verifyChain() {
        TenantContext.require();
        String prev = AuditHasher.GENESIS;
        long expectedSeq = 1;
        long checked = 0;
        long cursor = 0;
        while (true) {
            List<AuditEvent> batch = store.findAfter(cursor, BATCH);
            if (batch.isEmpty()) {
                return new Verification(true, checked, null, null);
            }
            for (AuditEvent e : batch) {
                if (e.seq() != expectedSeq) {
                    return new Verification(false, checked, expectedSeq, "Falta el evento seq=" + expectedSeq);
                }
                if (!e.isConsistentWith(prev)) {
                    return new Verification(false, checked, e.seq(), "Hash o enlace previo invalido en seq=" + e.seq());
                }
                prev = e.hash();
                expectedSeq++;
                checked++;
                cursor = e.seq();
            }
        }
    }
}
