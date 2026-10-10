package bo.solid.fiscal.application.idempotency.port;

import bo.solid.fiscal.application.idempotency.StoredResponse;
import java.util.Optional;

public interface IdempotencyStore {

    record Record(String requestHash, String status, StoredResponse response) {}

    /** Inserta la clave si no existe (INSERT .. ON CONFLICT DO NOTHING). Bloquea si otra TX la esta insertando. */
    boolean tryBegin(String key, String requestHash);

    Optional<Record> find(String key);

    void complete(String key, StoredResponse response);
}
