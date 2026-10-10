package bo.solid.fiscal.domain.org;

import java.time.Instant;
import java.util.UUID;

/** Token delegado del contribuyente, SIEMPRE cifrado. El texto plano nunca vive en este objeto. */
public record FiscalToken(UUID id, UUID tenantId, UUID companyId, String ciphertext, String keyId, Instant expiresAt,
                          String status, Instant createdAt, String createdBy) {

    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }

    @Override
    public String toString() {
        return "FiscalToken[id=" + id + ", companyId=" + companyId + ", keyId=" + keyId + ", expiresAt=" + expiresAt
                + ", status=" + status + "]";
    }
}
