package bo.solid.fiscal.infrastructure.outbox;

import bo.solid.fiscal.application.outbox.OutboxDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "solid.fiscal.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxDispatcher dispatcher;
    private final int batchSize;

    OutboxScheduler(OutboxDispatcher dispatcher, @Value("${solid.fiscal.outbox.relay.batch-size:100}") int batchSize) {
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${solid.fiscal.outbox.relay.interval-ms:1000}")
    void run() {
        try {
            OutboxDispatcher.Summary s = dispatcher.dispatchBatch(batchSize);
            if (s.published() + s.failed() + s.dead() > 0) {
                log.debug("outbox: {}", s);
            }
        } catch (RuntimeException e) {
            log.error("Fallo del ciclo del relay de outbox", e);
        }
    }
}
