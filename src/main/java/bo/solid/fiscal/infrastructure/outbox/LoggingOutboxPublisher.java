package bo.solid.fiscal.infrastructure.outbox;

import bo.solid.fiscal.application.outbox.OutboxRecord;
import bo.solid.fiscal.application.outbox.port.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publicador por defecto (sin Kafka): solo registra. Util en desarrollo. */
@Component
@ConditionalOnProperty(name = "solid.fiscal.outbox.kafka.enabled", havingValue = "false", matchIfMissing = true)
class LoggingOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxRecord r) {
        log.info("[outbox] {} {} aggregate={} tenant={}", r.eventType(), r.id(), r.aggregateId(), r.tenantId());
    }
}
