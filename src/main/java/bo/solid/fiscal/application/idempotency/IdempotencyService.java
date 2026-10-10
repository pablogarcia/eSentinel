package bo.solid.fiscal.application.idempotency;

import bo.solid.fiscal.application.idempotency.port.IdempotencyStore;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.shared.FiscalException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotencia (ADR-0005): clave + efecto + respuesta se confirman en UNA transaccion. Si la TX falla, la clave
 * no se consume y el cliente puede reintentar. Misma clave + mismo payload => misma respuesta; distinto payload => 409.
 */
@Service
public class IdempotencyService {

    public record Result(StoredResponse response, boolean replayed) {}

    private final IdempotencyStore store;

    public IdempotencyService(IdempotencyStore store) {
        this.store = store;
    }

    @Transactional
    public Result execute(String key, String requestHash, Supplier<StoredResponse> operation) {
        TenantContext.require();
        boolean first = store.tryBegin(key, requestHash);
        if (first) {
            StoredResponse response = operation.get();
            store.complete(key, response);
            return new Result(response, false);
        }
        IdempotencyStore.Record existing = store.find(key).orElseThrow(() ->
                FiscalException.conflict("IDEMPOTENCY_004", "Estado de idempotencia inconsistente; reintente"));
        if (!existing.requestHash().equals(requestHash)) {
            throw FiscalException.conflict("IDEMPOTENCY_002",
                    "La Idempotency-Key ya se uso con una solicitud distinta");
        }
        if (!"COMPLETED".equals(existing.status()) || existing.response() == null) {
            throw FiscalException.conflict("IDEMPOTENCY_003", "La operacion con esa Idempotency-Key sigue en curso");
        }
        return new Result(existing.response(), true);
    }
}
