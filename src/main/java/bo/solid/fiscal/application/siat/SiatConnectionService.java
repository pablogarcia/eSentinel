package bo.solid.fiscal.application.siat;

import bo.solid.fiscal.application.siat.port.SiatPort;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.siat.CommunicationStatus;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatMessage;
import bo.solid.fiscal.domain.siat.SiatScope;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Casos de uso de conectividad con el SIAT. Deliberadamente SIN @Transactional: la llamada remota no retiene conexion a BD. */
@Service
public class SiatConnectionService {

    public record ConnectivityResult(boolean reachable, boolean serviceOk, List<SiatMessage> messages, long latencyMs) {}

    private final SiatCredentialsLoader credentials;
    private final SiatCallExecutor executor;
    private final SiatPort siat;

    public SiatConnectionService(SiatCredentialsLoader credentials, SiatCallExecutor executor, SiatPort siat) {
        this.credentials = credentials;
        this.executor = executor;
        this.siat = siat;
    }

    public ConnectivityResult checkCommunication(UUID companyId) {
        TenantContext.require();
        SiatCredentials c = credentials.load(companyId, false);
        long t0 = System.nanoTime();
        CommunicationStatus status = executor.execute(companyId, "FacturacionCodigos", "verificarComunicacion", null,
                () -> siat.verifyCommunication(c));
        return new ConnectivityResult(true, status.ok(), status.messages(), (System.nanoTime() - t0) / 1_000_000);
    }

    /** Hora oficial del SIAT; se usara antes de cada CUFD (S4). Requiere CUIS y codigoSistema. */
    public SiatDateTime fetchServerTime(UUID companyId, SiatScope scope) {
        TenantContext.require();
        SiatCredentials c = credentials.load(companyId, true);
        return executor.execute(companyId, "FacturacionSincronizacion", "sincronizarFechaHora", null,
                () -> siat.synchronizeDateTime(c, scope));
    }
}
