package bo.solid.fiscal.application.siat.port;

import java.util.UUID;

/** Bitacora de llamadas al SIAT. begin() y finish() confirman en transacciones propias e independientes. */
public interface SiatTransactionLog {

    enum Outcome { OK, SIAT_ERROR, AUTH_ERROR, TIMEOUT, UNAVAILABLE, PROTOCOL_ERROR }

    UUID begin(UUID companyId, String service, String operation, UUID documentId, String correlationId);

    void finish(UUID id, Outcome outcome, String siatCodes, String errorMessage, long latencyMs);
}
