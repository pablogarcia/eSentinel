package bo.solid.fiscal.infrastructure.outbox;

import bo.solid.fiscal.application.outbox.OutboxRecord;
import bo.solid.fiscal.application.outbox.port.OutboxPublisher;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a Kafka: topic = prefijo + aggregateType, key = aggregateId (orden por agregado). Espera el ack del broker;
 * si falla lanza y el dispatcher reintenta con backoff. Kafka solo transporta: la verdad esta en PostgreSQL.
 */
@Component
@ConditionalOnProperty(name = "solid.fiscal.outbox.kafka.enabled", havingValue = "true")
class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final String topicPrefix;
    private final long ackTimeoutSeconds;

    KafkaOutboxPublisher(KafkaTemplate<String, String> kafka,
                         @Value("${solid.fiscal.outbox.kafka.topic-prefix:solid.fiscal.}") String topicPrefix,
                         @Value("${solid.fiscal.outbox.kafka.ack-timeout-seconds:10}") long ackTimeoutSeconds) {
        this.kafka = kafka;
        this.topicPrefix = topicPrefix;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
    }

    @Override
    public void publish(OutboxRecord r) throws Exception {
        ProducerRecord<String, String> rec = new ProducerRecord<>(
                topicPrefix + r.aggregateType().toLowerCase(Locale.ROOT), r.aggregateId().toString(), r.payloadJson());
        rec.headers().add("event_id", r.id().toString().getBytes(StandardCharsets.UTF_8));
        rec.headers().add("event_type", r.eventType().getBytes(StandardCharsets.UTF_8));
        rec.headers().add("tenant_id", r.tenantId().toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(rec).get(ackTimeoutSeconds, TimeUnit.SECONDS);
    }
}
