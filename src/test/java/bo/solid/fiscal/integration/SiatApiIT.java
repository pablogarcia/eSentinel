package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SiatApiIT extends PostgresIT {

    static final String TOKEN = "API-TOKEN-NUNCA-DEBE-SALIR";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @BeforeEach
    void reset() { SIAT.reset(); }

    private record Ctx(String tenant, String company) {}

    private Ctx setup(long nit) throws Exception {
        String t = json.readTree(mvc.perform(post("/api/v1/tenants").with(Jwts.platform()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"S " + UUID.randomUUID() + "\"}")).andReturn().getResponse().getContentAsString()).get("id").asText();
        String c = json.readTree(mvc.perform(post("/api/v1/companies").with(Jwts.admin(t)).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nit\":" + nit + ",\"razonSocial\":\"X\",\"environment\":\"PILOT\"}")).andReturn().getResponse().getContentAsString()).get("id").asText();
        return new Ctx(t, c);
    }

    private String tokenBody() {
        return "{\"token\":\"" + TOKEN + "\",\"expiresAt\":\"" + Instant.now().plusSeconds(86_400) + "\"}";
    }

    @Test
    void token_is_write_only_and_connectivity_works_end_to_end() throws Exception {
        Ctx x = setup(9_200_001L);
        String put = mvc.perform(put("/api/v1/companies/" + x.company() + "/siat-token").with(Jwts.admin(x.tenant()))
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(true))
                .andReturn().getResponse().getContentAsString();
        String get = mvc.perform(get("/api/v1/companies/" + x.company() + "/siat-token").with(Jwts.admin(x.tenant())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(put).doesNotContain(TOKEN);
        assertThat(get).doesNotContain(TOKEN);

        mvc.perform(post("/api/v1/companies/" + x.company() + "/siat/connectivity-check").with(Jwts.admin(x.tenant())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reachable").value(true))
                .andExpect(jsonPath("$.serviceOk").value(true)).andExpect(jsonPath("$.latencyMs").isNumber());
        assertThat(SIAT.last().header("apikey")).isEqualTo("TokenApi " + TOKEN);
    }

    @Test
    void upstream_failures_map_to_distinct_http_statuses_and_categories() throws Exception {
        Ctx x = setup(9_200_002L);
        mvc.perform(put("/api/v1/companies/" + x.company() + "/siat-token").with(Jwts.admin(x.tenant()))
                .contentType(MediaType.APPLICATION_JSON).content(tokenBody())).andExpect(status().isOk());
        String url = "/api/v1/companies/" + x.company() + "/siat/connectivity-check";

        SIAT.respondWith(r -> FakeSiatServer.Response.fault("API KEY NO VALIDO"));
        mvc.perform(post(url).with(Jwts.admin(x.tenant()))).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SIAT_AUTH_001")).andExpect(jsonPath("$.category").value("SIAT"));

        SIAT.respondWith(r -> FakeSiatServer.Response.http(503));
        mvc.perform(post(url).with(Jwts.admin(x.tenant()))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.category").value("NETWORK"));

        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.COMM_OK).delayed(4000));
        mvc.perform(post(url).with(Jwts.admin(x.tenant()))).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("SIAT_NET_002"));
    }

    @Test
    void validation_and_authorization_rules() throws Exception {
        Ctx x = setup(9_200_003L);
        String url = "/api/v1/companies/" + x.company();
        mvc.perform(put(url + "/siat-token").with(Jwts.of("s", x.tenant(), "SELLER")).contentType(MediaType.APPLICATION_JSON)
                .content(tokenBody())).andExpect(status().isForbidden());
        mvc.perform(put(url + "/siat-token").with(Jwts.admin(x.tenant())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"expiresAt\":\"" + Instant.now().plusSeconds(60) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put(url + "/siat-token").with(Jwts.admin(x.tenant())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(url + "/siat/connectivity-check").with(Jwts.admin(x.tenant())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIAT_CFG_004"));
        mvc.perform(post(url + "/siat/connectivity-check").with(Jwts.of("a", x.tenant(), "AUDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void system_code_can_be_assigned_and_is_audited() throws Exception {
        Ctx x = setup(9_200_004L);
        mvc.perform(put("/api/v1/companies/" + x.company() + "/system-code").with(Jwts.admin(x.tenant()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"systemCode\":\"ABC123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.systemCode").value("ABC123"));
        mvc.perform(get("/api/v1/companies/" + x.company()).with(Jwts.admin(x.tenant())))
                .andExpect(jsonPath("$.systemCode").value("ABC123"));
        String audit = mvc.perform(get("/api/v1/audit-events").with(Jwts.of("a", x.tenant(), "AUDITOR")))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("SystemCodeAssigned").doesNotContain(TOKEN);
    }
}
