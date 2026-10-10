package bo.solid.fiscal.interfaces.rest;

import bo.solid.fiscal.application.audit.AuditService;
import bo.solid.fiscal.domain.audit.AuditEvent;
import bo.solid.fiscal.interfaces.security.Roles;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    record AuditEventResponse(UUID id, long seq, Instant occurredAt, String actorId, String actorRoles,
                              String operation, String entityType, UUID entityId, UUID documentId, String oldState,
                              String newState, String ip, String correlationId, String traceId, String hash) {}

    record Page(List<AuditEventResponse> items, Long nextAfterSeq) {}

    @GetMapping
    @PreAuthorize(Roles.CAN_AUDIT)
    Page list(@RequestParam(defaultValue = "0") long afterSeq, @RequestParam(defaultValue = "100") int limit) {
        List<AuditEvent> events = audit.list(afterSeq, limit);
        List<AuditEventResponse> items = events.stream().map(e -> new AuditEventResponse(e.id(), e.seq(),
                e.data().occurredAt(), e.data().actorId(), e.data().actorRoles(), e.data().operation(),
                e.data().entityType(), e.data().entityId(), e.data().documentId(), e.data().oldState(),
                e.data().newState(), e.data().ip(), e.data().correlationId(), e.data().traceId(), e.hash())).toList();
        Long next = items.isEmpty() ? null : items.get(items.size() - 1).seq();
        return new Page(items, next);
    }

    @GetMapping("/verification")
    @PreAuthorize(Roles.CAN_AUDIT)
    AuditService.Verification verify() {
        return audit.verifyChain();
    }
}
