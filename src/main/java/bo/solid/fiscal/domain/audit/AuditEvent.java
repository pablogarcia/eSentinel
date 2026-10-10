package bo.solid.fiscal.domain.audit;

import java.util.UUID;

/** Evento ya encadenado. hash = SHA-256(prevHash || canonical(draft, seq)). */
public record AuditEvent(UUID id, long seq, AuditDraft data, String prevHash, String hash) {

    public static AuditEvent seal(UUID id, long seq, String prevHash, AuditDraft data) {
        return new AuditEvent(id, seq, data, prevHash, AuditHasher.hash(prevHash, seq, data));
    }

    /** true si el hash almacenado corresponde al contenido y al hash previo esperado. */
    public boolean isConsistentWith(String expectedPrevHash) {
        return prevHash.equals(expectedPrevHash) && hash.equals(AuditHasher.hash(prevHash, seq, data));
    }
}
