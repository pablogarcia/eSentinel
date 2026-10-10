package bo.solid.fiscal.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.solid.fiscal.application.outbox.OutboxRecord;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxPublisherTest {

    @SuppressWarnings("unchecked")
    private static Object publisher(KafkaTemplate<String, String> kafka) throws Exception {
        Class<?> c = Class.forName("bo.solid.fiscal.infrastructure.outbox.KafkaOutboxPublisher");
        Constructor<?> ctor = c.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return ctor.newInstance(kafka, "solid.fiscal.", 1L);
    }

    private static void publish(Object p, OutboxRecord r) throws Exception {
        var m = p.getClass().getDeclaredMethod("publish", OutboxRecord.class);
        m.setAccessible(true);
        try {
            m.invoke(p, r);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sends_to_topic_by_aggregate_type_keyed_by_aggregate_id_with_event_headers() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        UUID id = UUID.randomUUID(), tenant = UUID.randomUUID(), agg = UUID.randomUUID();

        publish(publisher(kafka), new OutboxRecord(id, tenant, "COMPANY", agg, "CompanyRegistered", "{\"a\":1}", 0));

        ArgumentCaptor<ProducerRecord<String, String>> cap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(cap.capture());
        ProducerRecord<String, String> rec = cap.getValue();
        assertThat(rec.topic()).isEqualTo("solid.fiscal.company");
        assertThat(rec.key()).isEqualTo(agg.toString());
        assertThat(rec.value()).isEqualTo("{\"a\":1}");
        assertThat(new String(rec.headers().lastHeader("event_id").value(), StandardCharsets.UTF_8)).isEqualTo(id.toString());
        assertThat(new String(rec.headers().lastHeader("event_type").value(), StandardCharsets.UTF_8)).isEqualTo("CompanyRegistered");
        assertThat(new String(rec.headers().lastHeader("tenant_id").value(), StandardCharsets.UTF_8)).isEqualTo(tenant.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void propagates_broker_failures_so_the_dispatcher_can_retry() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));
        assertThatThrownBy(() -> publish(publisher(kafka),
                new OutboxRecord(UUID.randomUUID(), UUID.randomUUID(), "COMPANY", UUID.randomUUID(), "X", "{}", 0)))
                .isInstanceOf(Exception.class);
    }
}
