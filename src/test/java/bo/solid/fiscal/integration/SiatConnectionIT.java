package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.application.siat.SiatConnectionService;
import bo.solid.fiscal.application.siat.TokenService;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatScope;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SiatConnectionIT extends PostgresIT {

    static final String TOKEN = "TOKEN-DELEGADO-DE-PRUEBA-XYZ";

    @Autowired OrganizationService org;
    @Autowired TokenService tokens;
    @Autowired SiatConnectionService connection;

    @BeforeEach
    void reset() { SIAT.reset(); }

    private String q(String sql, Object... params) throws Exception {
        try (Connection c = owner(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private UUID newTenant() { return org.createTenant("Siat " + UUID.randomUUID()).id(); }

    private Company newCompany(UUID tenant, long nit) {
        return TenantContext.runAs(tenant, () ->
                org.registerCompany(nit, "Empresa SIAT", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
    }

    private static Instant inOneDay() { return Instant.now().plus(Duration.ofDays(1)); }

    @Test
    void token_is_stored_encrypted_bound_to_its_company_and_never_in_plaintext() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_001L);
        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));

        String stored = q("SELECT token_ciphertext FROM fiscal_token WHERE company_id = ?::uuid", c.id().toString());
        assertThat(stored).startsWith("v1:test1:").doesNotContain(TOKEN);
        assertThat(q("SELECT key_id FROM fiscal_token WHERE company_id = ?::uuid", c.id().toString())).isEqualTo("test1");
        assertThat(TenantContext.runAs(t, () -> tokens.resolveActivePlaintext(c.id()))).isEqualTo(TOKEN);

        // El ciphertext copiado a OTRA empresa del mismo tenant no descifra (AAD distinta).
        Company other = newCompany(t, 9_100_002L);
        try (Connection conn = owner(); PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO fiscal_token (id, tenant_id, company_id, token_ciphertext, key_id, expires_at, created_by)
                VALUES (?, ?, ?, ?, 'test1', now() + interval '1 day', 'attacker')""")) {
            ps.setObject(1, UUID.randomUUID()); ps.setObject(2, t); ps.setObject(3, other.id()); ps.setString(4, stored);
            ps.execute();
        }
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> tokens.resolveActivePlaintext(other.id())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registering_a_new_token_revokes_the_previous_one() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_003L);
        TenantContext.runAs(t, () -> tokens.register(c.id(), "primero", inOneDay()));
        TenantContext.runAs(t, () -> tokens.register(c.id(), "segundo", inOneDay()));

        assertThat(q("SELECT count(*) FROM fiscal_token WHERE company_id = ?::uuid AND status = 'ACTIVE'", c.id().toString()))
                .isEqualTo("1");
        assertThat(q("SELECT count(*) FROM fiscal_token WHERE company_id = ?::uuid AND status = 'REVOKED'", c.id().toString()))
                .isEqualTo("1");
        assertThat(TenantContext.runAs(t, () -> tokens.resolveActivePlaintext(c.id()))).isEqualTo("segundo");
    }

    @Test
    void invalid_tokens_and_past_expirations_are_rejected_and_the_audit_never_holds_the_token() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_004L);
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> tokens.register(c.id(), "  ", inOneDay())))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_CFG_001"));
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, Instant.now().minusSeconds(5))))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_CFG_003"));

        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));
        assertThat(q("SELECT count(*) FROM audit_event WHERE tenant_id = ?::uuid AND operation = 'FiscalTokenRegistered'",
                t.toString())).isEqualTo("1");
        assertThat(q("SELECT count(*) FROM audit_event WHERE tenant_id = ?::uuid AND new_state LIKE ?", t.toString(), "%" + TOKEN + "%"))
                .isEqualTo("0");
    }

    @Test
    void connectivity_check_logs_a_completed_siat_transaction() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_005L);
        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));

        SiatConnectionService.ConnectivityResult r = TenantContext.runAs(t, () -> connection.checkCommunication(c.id()));

        assertThat(r.reachable()).isTrue();
        assertThat(r.serviceOk()).isTrue();
        assertThat(SIAT.last().header("apikey")).isEqualTo("TokenApi " + TOKEN);
        assertThat(q("SELECT outcome FROM siat_transaction WHERE company_id = ?::uuid", c.id().toString())).isEqualTo("OK");
        assertThat(q("SELECT operation FROM siat_transaction WHERE company_id = ?::uuid", c.id().toString()))
                .isEqualTo("verificarComunicacion");
        assertThat(q("SELECT finished_at IS NOT NULL AND latency_ms >= 0 FROM siat_transaction WHERE company_id = ?::uuid",
                c.id().toString())).isEqualTo("t");
    }

    @Test
    void failures_are_logged_with_the_right_outcome_and_survive_the_failed_operation() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_006L);
        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));

        SIAT.respondWith(r -> FakeSiatServer.Response.fault("API KEY NO VALIDO"));
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.checkCommunication(c.id())))
                .isInstanceOf(FiscalException.class);
        SIAT.respondWith(r -> FakeSiatServer.Response.http(503));
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.checkCommunication(c.id())))
                .isInstanceOf(FiscalException.class);
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.COMM_OK).delayed(4000));
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.checkCommunication(c.id())))
                .isInstanceOf(FiscalException.class);

        assertThat(q("SELECT string_agg(outcome, ',' ORDER BY started_at) FROM siat_transaction WHERE company_id = ?::uuid",
                c.id().toString())).isEqualTo("AUTH_ERROR,UNAVAILABLE,TIMEOUT");
        assertThat(q("SELECT count(*) FROM siat_transaction WHERE company_id = ?::uuid AND outcome = 'STARTED'",
                c.id().toString())).isEqualTo("0");
    }

    @Test
    void missing_or_expired_token_blocks_the_call_before_touching_the_siat() throws Exception {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_007L);
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.checkCommunication(c.id())))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_CFG_004"));

        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));
        try (Connection conn = owner(); PreparedStatement ps = conn.prepareStatement(
                "UPDATE fiscal_token SET expires_at = now() - interval '1 minute' WHERE company_id = ?")) {
            ps.setObject(1, c.id()); ps.execute();
        }
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.checkCommunication(c.id())))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_CFG_005"));
        assertThat(SIAT.requests).isEmpty();
    }

    @Test
    void another_tenant_can_neither_see_nor_use_a_companys_token() {
        UUID a = newTenant(), b = newTenant();
        Company c = newCompany(a, 9_100_008L);
        TenantContext.runAs(a, () -> tokens.register(c.id(), TOKEN, inOneDay()));
        assertThatThrownBy(() -> TenantContext.runAs(b, () -> connection.checkCommunication(c.id())))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("ORG_002"));
        assertThatThrownBy(() -> TenantContext.runAs(b, () -> tokens.status(c.id()))).isInstanceOf(FiscalException.class);
        assertThat(SIAT.requests).isEmpty();
    }

    @Test
    void server_time_needs_the_system_code_and_then_uses_it() {
        UUID t = newTenant();
        Company c = newCompany(t, 9_100_009L);
        TenantContext.runAs(t, () -> tokens.register(c.id(), TOKEN, inOneDay()));
        assertThatThrownBy(() -> TenantContext.runAs(t, () -> connection.fetchServerTime(c.id(), new SiatScope(0, 0, "CUIS"))))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_CFG_006"));

        TenantContext.runAs(t, () -> org.setSystemCode(c.id(), "SYS-777"));
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.fechaHora("2026-10-09T10:00:00.000")));
        SiatDateTime time = TenantContext.runAs(t, () -> connection.fetchServerTime(c.id(), new SiatScope(0, 0, "CUIS")));
        assertThat(time.ok()).isTrue();
        assertThat(SIAT.last().body()).contains("<codigoSistema>SYS-777</codigoSistema>");
    }
}
