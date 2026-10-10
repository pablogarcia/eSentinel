package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.application.outbox.OutboxDispatcher;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(RecordingPublisherConfig.class)
class OutboxIT extends PostgresIT {

    @Autowired OrganizationService org;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired RecordingPublisherConfig.Recorder publisher;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    @BeforeEach
    void reset() {
        publisher.failing.set(false);
    }

    private String ownerQuery(String sql, Object... params) throws Exception {
        try (Connection c = owner(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void ownerExec(String sql, Object... params) throws Exception {
        try (Connection c = owner(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.execute();
        }
    }

    private void drain() {
        for (int i = 0; i < 200; i++) {
            OutboxDispatcher.Summary s = dispatcher.dispatchBatch(500);
            if (s.published() + s.failed() + s.dead() == 0) return;
        }
    }

    @Test
    void business_change_and_outbox_event_commit_atomically_and_get_published() throws Exception {
        UUID t = org.createTenant("Outbox " + UUID.randomUUID()).id();
        Company c = TenantContext.runAs(t, () -> org.registerCompany(8_000_001L, "E", Environment.PILOT,
                Modality.ELECTRONIC_ONLINE, null));
        assertThat(ownerQuery("SELECT count(*) FROM outbox_event WHERE tenant_id = ?::uuid AND status = 'PENDING'", t.toString()))
                .isEqualTo("2");   // TenantCreated + CompanyRegistered

        drain();

        assertThat(ownerQuery("SELECT count(*) FROM outbox_event WHERE tenant_id = ?::uuid AND status = 'PUBLISHED'", t.toString()))
                .isEqualTo("2");
        assertThat(ownerQuery("SELECT payload->>'nit' FROM outbox_event WHERE aggregate_id = ?::uuid AND event_type = 'CompanyRegistered'",
                c.id().toString())).isEqualTo("8000001");
    }

    @Test
    void a_rolled_back_change_leaves_no_outbox_event_and_no_audit_event() throws Exception {
        UUID t = org.createTenant("Rollback " + UUID.randomUUID()).id();
        TenantContext.runAs(t, () -> org.registerCompany(8_000_002L, "E", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        assertThatThrownBy(() -> TenantContext.runAs(t, () ->
                org.registerCompany(8_000_002L, "dup", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(ownerQuery("SELECT count(*) FROM outbox_event WHERE tenant_id = ?::uuid AND event_type = 'CompanyRegistered'",
                t.toString())).isEqualTo("1");
        assertThat(ownerQuery("SELECT count(*) FROM audit_event WHERE tenant_id = ?::uuid AND operation = 'CompanyRegistered'",
                t.toString())).isEqualTo("1");
    }

    @Test
    void broker_failure_backs_off_then_recovers_without_losing_the_event() throws Exception {
        UUID t = org.createTenant("Backoff " + UUID.randomUUID()).id();
        drain();   // limpia pendientes de otros tests y del propio alta

        publisher.failing.set(true);
        TenantContext.runAs(t, () -> org.registerCompany(8_000_003L, "E", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        OutboxDispatcher.Summary failed = dispatcher.dispatchBatch(500);
        assertThat(failed.failed()).isGreaterThanOrEqualTo(1);
        assertThat(ownerQuery("SELECT attempts FROM outbox_event WHERE tenant_id = ?::uuid AND event_type = 'CompanyRegistered'",
                t.toString())).isEqualTo("1");
        assertThat(ownerQuery("SELECT next_attempt_at > now() FROM outbox_event WHERE tenant_id = ?::uuid AND event_type = 'CompanyRegistered'",
                t.toString())).isEqualTo("t");

        // Broker recuperado, pero el backoff aun no vence => no se reintenta todavia.
        publisher.failing.set(false);
        assertThat(dispatcher.dispatchBatch(500).published()).isZero();

        ownerExec("UPDATE outbox_event SET next_attempt_at = now() WHERE tenant_id = ?::uuid", t.toString());
        drain();
        assertThat(ownerQuery("SELECT status FROM outbox_event WHERE tenant_id = ?::uuid AND event_type = 'CompanyRegistered'",
                t.toString())).isEqualTo("PUBLISHED");
    }

    @Test
    void poison_messages_go_to_dead_after_max_attempts() throws Exception {
        UUID t = org.createTenant("Dead " + UUID.randomUUID()).id();
        drain();
        TenantContext.runAs(t, () -> org.registerCompany(8_000_004L, "E", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        ownerExec("UPDATE outbox_event SET attempts = ? WHERE tenant_id = ?::uuid", OutboxDispatcher.MAX_ATTEMPTS - 1, t.toString());
        publisher.failing.set(true);
        assertThat(dispatcher.dispatchBatch(500).dead()).isGreaterThanOrEqualTo(1);
        assertThat(ownerQuery("SELECT status FROM outbox_event WHERE tenant_id = ?::uuid AND event_type = 'CompanyRegistered'",
                t.toString())).isEqualTo("DEAD");
    }

    @Test
    void concurrent_relays_publish_every_event_exactly_once() throws Exception {
        UUID t = org.createTenant("Skip " + UUID.randomUUID()).id();
        drain();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            ownerExec("""
                    INSERT INTO outbox_event (id, tenant_id, aggregate_type, aggregate_id, event_type, payload)
                    VALUES (?, ?, 'COMPANY', ?, 'Seeded', '{}'::jsonb)""", id, t, UUID.randomUUID());
        }
        int relays = 4;
        ExecutorService pool = Executors.newFixedThreadPool(relays);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int r = 0; r < relays; r++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 40; i++) dispatcher.dispatchBatch(7);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        for (UUID id : ids) {
            assertThat(publisher.published.get(id)).as("evento %s", id).isNotNull();
            assertThat(publisher.published.get(id).get()).as("veces publicado %s", id).isEqualTo(1);
        }
    }

    @Test
    void tenants_cannot_see_other_tenants_outbox_and_app_cannot_update_or_use_relay_views() {
        UUID a = org.createTenant("A " + UUID.randomUUID()).id();
        UUID b = org.createTenant("B " + UUID.randomUUID()).id();
        Integer seenByB = TenantContext.runAs(b, () -> tx.execute(s ->
                jdbc.sql("SELECT count(*) FROM outbox_event WHERE tenant_id = ?").param(a).query(Integer.class).single()));
        assertThat(seenByB).isZero();
        Integer seenWithoutTenant = tx.execute(s -> jdbc.sql("SELECT count(*) FROM outbox_event").query(Integer.class).single());
        assertThat(seenWithoutTenant).isZero();
        assertThatThrownBy(() -> TenantContext.runAs(a, () -> tx.execute(s ->
                jdbc.sql("UPDATE outbox_event SET status = 'DEAD'").update()))).rootCause()
                .hasMessageContaining("permission denied");
    }
}
