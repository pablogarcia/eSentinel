package bo.solid.fiscal.domain.siat;

import java.util.List;

/** Resultado de verificarComunicacion: transaccion=true significa que el servicio del SIAT esta operativo. */
public record CommunicationStatus(boolean ok, List<SiatMessage> messages) {
    public CommunicationStatus {
        messages = List.copyOf(messages);
    }
}
