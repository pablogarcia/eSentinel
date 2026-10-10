package bo.solid.fiscal.application.outbox.port;

import bo.solid.fiscal.application.outbox.OutboxRecord;
import java.util.List;
import java.util.UUID;

/** Operaciones del relay. Cruzan tenants (rol fiscal_relay_role); no usar fuera del dispatcher. */
public interface OutboxStore {
    /** Reclama hasta n mensajes PENDING vencidos con FOR UPDATE SKIP LOCKED (dentro de la TX actual). */
    List<OutboxRecord> claimBatch(int n);

    void markPublished(UUID id);

    /** Incrementa intentos, programa el siguiente con backoff exponencial o marca DEAD al superar maxAttempts. */
    void markFailed(UUID id, int newAttempts, boolean dead, long delaySeconds, String error);
}
