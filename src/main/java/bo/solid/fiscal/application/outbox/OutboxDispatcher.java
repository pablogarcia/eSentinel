package bo.solid.fiscal.application.outbox;

import bo.solid.fiscal.application.outbox.port.OutboxPublisher;
import bo.solid.fiscal.application.outbox.port.OutboxStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relay at-least-once: reclama un lote, publica y marca. Varias instancias pueden correr a la vez (SKIP LOCKED).
 * Los consumidores deben ser idempotentes por event id. Kafka NO es fuente de verdad: PostgreSQL lo es.
 */
@Service
public class OutboxDispatcher {

    public static final int MAX_ATTEMPTS = 10;
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    public record Summary(int published, int failed, int dead) {}

    private final OutboxStore store;
    private final OutboxPublisher publisher;

    public OutboxDispatcher(OutboxStore store, OutboxPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @Transactional
    public Summary dispatchBatch(int batchSize) {
        List<OutboxRecord> batch = store.claimBatch(batchSize);
        int ok = 0, failed = 0, dead = 0;
        for (OutboxRecord r : batch) {
            try {
                publisher.publish(r);
                store.markPublished(r.id());
                ok++;
            } catch (Exception e) {
                int attempts = r.attempts() + 1;
                boolean isDead = attempts >= MAX_ATTEMPTS;
                long delay = Math.min(300L, 1L << Math.min(attempts, 10));
                store.markFailed(r.id(), attempts, isDead, delay, abbreviate(e));
                log.warn("Fallo publicando outbox {} ({}), intento {}{}", r.id(), r.eventType(), attempts,
                        isDead ? " -> DEAD" : "", e);
                if (isDead) dead++; else failed++;
            }
        }
        return new Summary(ok, failed, dead);
    }

    private static String abbreviate(Exception e) {
        String m = e.getClass().getSimpleName() + ": " + e.getMessage();
        return m.length() > 1000 ? m.substring(0, 1000) : m;
    }
}
