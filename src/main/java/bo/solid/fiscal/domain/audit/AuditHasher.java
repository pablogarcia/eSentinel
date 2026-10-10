package bo.solid.fiscal.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** Hash canonico de la cadena de auditoria. Cada campo se codifica como longitud:valor para evitar ambiguedades. */
public final class AuditHasher {

    public static final String GENESIS = "0".repeat(64);

    private AuditHasher() {}

    public static String hash(String prevHash, long seq, AuditDraft d) {
        String canonical = canonical(seq, d);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(prevHash.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '|');
            sha.update(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    static String canonical(long seq, AuditDraft d) {
        List<String> fields = java.util.Arrays.asList(
                d.tenantId().toString(), Long.toString(seq), d.occurredAt().toString(), d.actorId(), d.actorRoles(),
                d.operation(), d.entityType(), str(d.entityId()), str(d.documentId()), d.oldState(), d.newState(),
                d.siatRequestRef(), d.siatResponseRef(), d.ip(), d.correlationId(), d.traceId());
        return fields.stream().map(AuditHasher::enc).collect(Collectors.joining("|"));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String enc(String v) { return v == null ? "~" : v.length() + ":" + v; }
}
