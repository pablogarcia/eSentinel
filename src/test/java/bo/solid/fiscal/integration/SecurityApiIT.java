package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiIT extends PostgresIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tenant() throws Exception {
        String res = mvc.perform(post("/api/v1/tenants").with(Jwts.platform()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T " + UUID.randomUUID() + "\"}")).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    private static final String COMPANY = "{\"nit\":%d,\"razonSocial\":\"X\",\"environment\":\"PILOT\"}";

    @Test
    void no_token_is_401_with_security_error_model() throws Exception {
        mvc.perform(get("/api/v1/companies")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.category").value("SECURITY")).andExpect(jsonPath("$.code").value("SECURITY_001"));
    }

    @Test
    void seller_cannot_register_companies_but_can_read() throws Exception {
        String t = tenant();
        mvc.perform(post("/api/v1/companies").with(Jwts.of("bob", t, "SELLER")).header("Idempotency-Key", "key-seller-0001")
                        .contentType(MediaType.APPLICATION_JSON).content(COMPANY.formatted(111111L)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SECURITY_004"));
        mvc.perform(get("/api/v1/companies").with(Jwts.of("bob", t, "SELLER"))).andExpect(status().isOk());
    }

    @Test
    void tenant_roles_cannot_create_tenants() throws Exception {
        mvc.perform(post("/api/v1/tenants").with(Jwts.of("eve", UUID.randomUUID().toString(), "TENANT_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void super_admin_is_platform_only_and_cannot_read_tenant_data() throws Exception {
        mvc.perform(get("/api/v1/companies").with(Jwts.platform())).andExpect(status().isForbidden());
    }

    @Test
    void token_without_tenant_claim_or_with_garbage_tenant_is_rejected() throws Exception {
        mvc.perform(get("/api/v1/companies").with(Jwts.of("x", null, "TENANT_ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SECURITY_002"));
        mvc.perform(get("/api/v1/companies").with(Jwts.of("x", "not-a-uuid", "TENANT_ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SECURITY_003"));
    }

    @Test
    void audit_endpoints_require_auditor_or_tenant_admin() throws Exception {
        String t = tenant();
        mvc.perform(get("/api/v1/audit-events").with(Jwts.of("s", t, "SELLER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events").with(Jwts.of("a", t, "AUDITOR"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-events/verification").with(Jwts.of("a", t, "AUDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void audit_records_actor_roles_ip_correlation_and_trace_from_the_request() throws Exception {
        String t = tenant();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        mvc.perform(post("/api/v1/companies").with(Jwts.of("alice", t, "TENANT_ADMIN"))
                        .header("Idempotency-Key", "key-audit-0001").header("X-Correlation-Id", "corr-123")
                        .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                        .contentType(MediaType.APPLICATION_JSON).content(COMPANY.formatted(222222L)))
                .andExpect(status().isCreated());
        String body = mvc.perform(get("/api/v1/audit-events").with(Jwts.of("a", t, "AUDITOR")))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(body).get("items");
        assertThat(items).hasSize(2);   // TenantCreated (plataforma) + CompanyRegistered
        JsonNode created = items.get(1);
        assertThat(created.get("operation").asText()).isEqualTo("CompanyRegistered");
        assertThat(created.get("actorId").asText()).isEqualTo("alice");
        assertThat(created.get("actorRoles").asText()).isEqualTo("TENANT_ADMIN");
        assertThat(created.get("correlationId").asText()).isEqualTo("corr-123");
        assertThat(created.get("traceId").asText()).isEqualTo(traceId);
        assertThat(created.get("ip").asText()).isNotBlank();
        assertThat(items.get(0).get("actorId").asText()).isEqualTo("platform");
    }
}
