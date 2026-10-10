package bo.solid.fiscal.integration;

import bo.solid.fiscal.application.outbox.OutboxRecord;
import bo.solid.fiscal.application.outbox.port.OutboxPublisher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Publicador controlable: cuenta cuantas veces se publico cada evento y puede simular caidas del broker. */
@TestConfiguration
class RecordingPublisherConfig {

    static final class Recorder implements OutboxPublisher {
        final Map<UUID, AtomicInteger> published = new ConcurrentHashMap<>();
        final AtomicBoolean failing = new AtomicBoolean(false);

        @Override
        public void publish(OutboxRecord r) {
            if (failing.get()) throw new IllegalStateException("broker caido (simulado)");
            published.computeIfAbsent(r.id(), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    @Bean
    @Primary
    Recorder recordingPublisher() {
        return new Recorder();
    }
}
