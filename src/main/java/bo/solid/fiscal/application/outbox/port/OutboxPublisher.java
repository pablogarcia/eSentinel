package bo.solid.fiscal.application.outbox.port;

import bo.solid.fiscal.application.outbox.OutboxRecord;

/** Transporte de eventos (Kafka). Debe lanzar excepcion si el broker no confirma. */
public interface OutboxPublisher {
    void publish(OutboxRecord record) throws Exception;
}
