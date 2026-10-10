package bo.solid.fiscal.application.outbox.port;

import bo.solid.fiscal.application.outbox.OutboxMessage;
import java.util.UUID;

public interface OutboxWriter {
    /** Inserta el evento en la misma transaccion que el cambio de estado. */
    UUID append(OutboxMessage message);
}
