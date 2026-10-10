package bo.solid.fiscal.application.siat;

import bo.solid.fiscal.application.context.RequestContext;
import bo.solid.fiscal.application.siat.port.SiatTransactionLog;
import bo.solid.fiscal.domain.shared.FiscalException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Envuelve una llamada al SIAT con su bitacora: registra STARTED y confirma ANTES de llamar, ejecuta SIN transaccion
 * abierta, y completa con el resultado. Si el proceso muere a mitad queda un STARTED que la reconciliacion detecta.
 */
@Component
public class SiatCallExecutor {

    private final SiatTransactionLog log;

    public SiatCallExecutor(SiatTransactionLog log) {
        this.log = log;
    }

    public <T> T execute(UUID companyId, String service, String operation, UUID documentId, Supplier<T> call) {
        String correlation = RequestContext.currentOrSystem().correlationId();
        UUID txId = log.begin(companyId, service, operation, documentId, correlation);
        long t0 = System.nanoTime();
        try {
            T result = call.get();
            log.finish(txId, SiatTransactionLog.Outcome.OK, null, null, ms(t0));
            return result;
        } catch (FiscalException e) {
            log.finish(txId, outcome(e), e.code(), truncate(e.getMessage()), ms(t0));
            throw e;
        } catch (RuntimeException e) {
            log.finish(txId, SiatTransactionLog.Outcome.PROTOCOL_ERROR, null, truncate(e.getClass().getSimpleName()), ms(t0));
            throw e;
        }
    }

    private static SiatTransactionLog.Outcome outcome(FiscalException e) {
        return switch (e.kind()) {
            case UPSTREAM_TIMEOUT -> SiatTransactionLog.Outcome.TIMEOUT;
            case UPSTREAM_UNAVAILABLE -> SiatTransactionLog.Outcome.UNAVAILABLE;
            case UPSTREAM_REJECTED -> "SIAT_AUTH_001".equals(e.code())
                    ? SiatTransactionLog.Outcome.AUTH_ERROR : SiatTransactionLog.Outcome.PROTOCOL_ERROR;
            default -> SiatTransactionLog.Outcome.SIAT_ERROR;
        };
    }

    private static long ms(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 500 ? s.substring(0, 500) : s);
    }
}
