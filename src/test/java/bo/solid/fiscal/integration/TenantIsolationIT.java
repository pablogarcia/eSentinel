package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.shared.Uuid7;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Prueba de aislamiento multi-tenant contra PostgreSQL real con RLS (ADR-0002). */
@SpringBootTest
class TenantIsolationIT extends PostgresIT {

    @Autowired OrganizationService service;
    @Autowired JdbcClient jdbc;
    @Autowired TransactionTemplate tx;

    private UUID newTenant() {
        return service.createTenant("Tenant " + UUID.randomUUID()).id();
    }

    private long nit() {
        return 1_000_000_000L + (long) (Math.random() * 8_000_000_000L);
    }

    @Test
    void application_role_is_not_owner_and_cannot_bypass_rls() {
        Boolean bypass = jdbc.sql("SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname = current_user")
                .query(Boolean.class).single();
        assertThat(bypass).isFalse();
        String owner = jdbc.sql("SELECT tableowner FROM pg_tables WHERE tablename = 'company'")
                .query(String.class).single();
        assertThat(owner).isEqualTo("fiscal_owner");
    }

    @Test
    void tenant_b_cannot_see_or_load_tenant_a_data() {
        UUID a = newTenant(), b = newTenant();
        Company companyA = TenantContext.runAs(a, () ->
                service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));

        assertThat(TenantContext.runAs(b, () -> service.listCompanies())).isEmpty();
        assertThatThrownBy(() -> TenantContext.runAs(b, () -> service.getCompany(companyA.id())))
                .isInstanceOf(FiscalException.class)
                .extracting(e -> ((FiscalException) e).kind()).isEqualTo(FiscalException.Kind.NOT_FOUND);
        assertThat(TenantContext.runAs(a, () -> service.listCompanies()))
                .extracting(Company::id).contains(companyA.id());
    }

    @Test
    void no_tenant_in_context_sees_nothing_fail_closed() {
        UUID a = newTenant();
        TenantContext.runAs(a, () ->
                service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        Integer visible = tx.execute(s -> jdbc.sql("SELECT count(*) FROM company").query(Integer.class).single());
        assertThat(visible).isZero();
        // La API de aplicacion exige tenant: sin contexto se rechaza con SECURITY (401).
        assertThatThrownBy(() -> service.listCompanies()).isInstanceOf(FiscalException.class)
                .extracting(e -> ((FiscalException) e).kind()).isEqualTo(FiscalException.Kind.UNAUTHENTICATED);
    }

    @Test
    void tenant_b_cannot_insert_rows_owned_by_tenant_a() {
        UUID a = newTenant(), b = newTenant();
        assertThatThrownBy(() -> TenantContext.runAs(b, () -> tx.execute(s -> jdbc.sql("""
                INSERT INTO company (id, tenant_id, nit, razon_social, environment, modality)
                VALUES (?, ?, ?, 'Intruso', 2, 1)""").params(Uuid7.next(), a, nit()).update())))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void composite_fk_blocks_branch_pointing_to_other_tenants_company() {
        UUID a = newTenant(), b = newTenant();
        Company companyA = TenantContext.runAs(a, () ->
                service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        // Tenant B intenta colgar una sucursal (con SU tenant_id) de la empresa de A.
        assertThatThrownBy(() -> TenantContext.runAs(b, () -> tx.execute(s -> jdbc.sql("""
                INSERT INTO branch (id, tenant_id, company_id, codigo_sucursal, nombre, direccion, municipio)
                VALUES (?, ?, ?, 0, 'X', 'Y', 'Z')""").params(Uuid7.next(), b, companyA.id()).update())))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Y por el servicio: la empresa de A no existe para B.
        assertThatThrownBy(() -> TenantContext.runAs(b, () ->
                service.addBranch(companyA.id(), 0, "Matriz", "Av", "La Paz", null)))
                .isInstanceOf(FiscalException.class);
    }

    @Test
    void application_role_cannot_delete_fiscal_data() {
        UUID a = newTenant();
        TenantContext.runAs(a, () ->
                service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        assertThatThrownBy(() -> TenantContext.runAs(a, () -> tx.execute(s ->
                jdbc.sql("DELETE FROM company").update()))).rootCause().hasMessageContaining("permission denied");
    }

    @Test
    void tenant_value_does_not_leak_between_transactions_on_pooled_connections() {
        UUID a = newTenant();
        TenantContext.runAs(a, () ->
                service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        for (int i = 0; i < 50; i++) {
            Integer visible = tx.execute(s -> jdbc.sql("SELECT count(*) FROM company").query(Integer.class).single());
            assertThat(visible).isZero(); // sin tenant => nada, aunque la conexion se reutilice
        }
    }

    @Test
    void duplicate_nit_in_same_environment_conflicts_but_other_tenant_may_reuse_nit() {
        UUID a = newTenant(), b = newTenant();
        long nit = nit();
        TenantContext.runAs(a, () -> service.registerCompany(nit, "A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
        assertThatThrownBy(() -> TenantContext.runAs(a, () ->
                service.registerCompany(nit, "A2", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null)))
                .isInstanceOf(FiscalException.class)
                .extracting(e -> ((FiscalException) e).kind()).isEqualTo(FiscalException.Kind.CONFLICT);
        // Mismo NIT en produccion para A: permitido (otro ambiente).
        TenantContext.runAs(a, () -> service.registerCompany(nit, "A prod", Environment.PRODUCTION, Modality.ELECTRONIC_ONLINE, null));
        // Otro tenant puede registrar el mismo NIT (cada tenant tiene su propia relacion con el SIN).
        TenantContext.runAs(b, () -> service.registerCompany(nit, "B", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null));
    }

    @Test
    void unsupported_modality_is_rejected() {
        UUID a = newTenant();
        assertThatThrownBy(() -> TenantContext.runAs(a, () -> service.registerCompany(nit(), "A",
                Environment.PILOT, Modality.COMPUTERIZED_ONLINE, null)))
                .isInstanceOf(FiscalException.class)
                .extracting(e -> ((FiscalException) e).kind()).isEqualTo(FiscalException.Kind.UNPROCESSABLE);
    }

    @Test
    void branch_and_pos_hierarchy_persists_per_tenant() {
        UUID a = newTenant();
        TenantContext.runAs(a, () -> {
            Company c = service.registerCompany(nit(), "Empresa A", Environment.PILOT, Modality.ELECTRONIC_ONLINE, null);
            Branch b0 = service.addBranch(c.id(), 0, "Casa Matriz", "Av. Arce 1", "La Paz", "2846005");
            service.addPointOfSale(b0.id(), 1, "Caja 1", null);
            assertThat(service.listBranches(c.id())).hasSize(1);
            assertThat(service.listPointsOfSale(b0.id())).hasSize(1);
            assertThatThrownBy(() -> service.addPointOfSale(b0.id(), 1, "Duplicada", null))
                    .isInstanceOf(FiscalException.class);
            return null;
        });
    }
}
