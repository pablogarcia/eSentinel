package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.application.audit.AuditService;
import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.audit.AuditEvent;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class AuditChainIT extends PostgresIT {

    @Autowired OrganizationService org;
    @Autowired AuditService audit;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    private UUID tenantWithEvents(int branches) {
        UUID t = org.createTenant("Audit " + UUID.randomUUID()).id();
        TenantContext.runAs(t, () -> {
            Company c = org.registerCompany(1_000_000L + (long) (Math.random() * 1e9), "Empresa", Environment.PILOT,
                    Modality.ELECTRONIC_ONLINE, null);
            for (int i = 0; i < branches; i++) org.addBranch(c.id(), i, "S" + i, "Dir", "La Paz", null);
            return null;
        });
        return t;
    }

    private void ownerExec(String sql, Object... params) throws Exception {
        try (Connection c = owner(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.execute();
        }
    }

    @Test
    void events_are_sequenced_chained_and_verify_cleanly() {
        UUID t = tenantWithEvents(3);   // TenantCreated + CompanyRegistered + 3 BranchAdded
        List<AuditEvent> events = TenantContext.runAs(t, () -> audit.list(0, 100));
        assertThat(events).extracting(AuditEvent::seq).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(events.get(0).prevHash()).isEqualTo("0".repeat(64));
        for (int i = 1; i < events.size(); i++) {
            assertThat(events.get(i).prevHash()).isEqualTo(events.get(i - 1).hash());
        }
        AuditService.Verification v = TenantContext.runAs(t, audit::verifyChain);
        assertThat(v.valid()).isTrue();
        assertThat(v.eventsChecked()).isEqualTo(5);
    }

    @Test
    void application_role_cannot_update_or_delete_audit_events() {
        UUID t = tenantWithEvents(0);
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> tx.execute(s ->
                jdbc.sql("UPDATE audit_event SET actor_id = 'mallory'").update()))).rootCause()
                .hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> tx.execute(s ->
                jdbc.sql("DELETE FROM audit_event").update()))).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void even_the_table_owner_is_blocked_by_the_immutability_trigger() {
        UUID t = tenantWithEvents(0);
        assertThatThrownBy(() -> ownerExec("UPDATE audit_event SET actor_id = 'mallory' WHERE tenant_id = ?", t))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> ownerExec("DELETE FROM audit_event WHERE tenant_id = ?", t))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> ownerExec("TRUNCATE audit_event")).hasMessageContaining("append-only");
    }

    @Test
    void tampering_with_a_row_is_detected_at_that_sequence() throws Exception {
        UUID t = tenantWithEvents(3);
        // Un atacante con privilegio de propietario desactiva el trigger y reescribe la evidencia...
        ownerExec("ALTER TABLE audit_event DISABLE TRIGGER audit_event_immutable");
        try {
            ownerExec("UPDATE audit_event SET actor_id = 'mallory' WHERE tenant_id = ? AND seq = 3", t);
        } finally {
            ownerExec("ALTER TABLE audit_event ENABLE TRIGGER audit_event_immutable");
        }
        // ...pero la cadena de hashes lo delata.
        AuditService.Verification v = TenantContext.runAs(t, audit::verifyChain);
        assertThat(v.valid()).isFalse();
        assertThat(v.firstBrokenSeq()).isEqualTo(3L);
    }

    @Test
    void deleting_a_row_is_detected_as_a_gap() throws Exception {
        UUID t = tenantWithEvents(3);
        ownerExec("ALTER TABLE audit_event DISABLE TRIGGER audit_event_immutable");
        try {
            ownerExec("DELETE FROM audit_event WHERE tenant_id = ? AND seq = 2", t);
        } finally {
            ownerExec("ALTER TABLE audit_event ENABLE TRIGGER audit_event_immutable");
        }
        AuditService.Verification v = TenantContext.runAs(t, audit::verifyChain);
        assertThat(v.valid()).isFalse();
        assertThat(v.firstBrokenSeq()).isEqualTo(2L);
    }

    @Test
    void concurrent_writers_in_one_tenant_still_produce_a_gapless_valid_chain() throws Exception {
        UUID t = org.createTenant("Concurrent " + UUID.randomUUID()).id();
        Company c = TenantContext.runAs(t, () -> org.registerCompany(5_000_001L, "Empresa", Environment.PILOT,
                Modality.ELECTRONIC_ONLINE, null));
        int writers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            int code = i;
            futures.add(pool.submit(() -> TenantContext.runAs(t, () ->
                    org.addBranch(c.id(), code, "S" + code, "Dir", "La Paz", null))));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();

        List<AuditEvent> events = TenantContext.runAs(t, () -> audit.list(0, 500));
        assertThat(events).hasSize(2 + writers);
        assertThat(events).extracting(AuditEvent::seq).doesNotHaveDuplicates();
        assertThat(TenantContext.runAs(t, audit::verifyChain).valid()).isTrue();
    }

    @Test
    void tenants_cannot_read_each_others_audit_trail() {
        UUID a = tenantWithEvents(1), b = tenantWithEvents(1);
        assertThat(TenantContext.runAs(a, () -> audit.list(0, 100)))
                .allMatch(e -> e.data().tenantId().equals(a));
        assertThat(TenantContext.runAs(b, () -> audit.list(0, 100)))
                .allMatch(e -> e.data().tenantId().equals(b));
    }
}
