package bo.solid.fiscal.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OrganizationApiIT extends PostgresIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    String createTenant() throws Exception {
        String res = mvc.perform(post("/api/v1/tenants").with(Jwts.platform()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    String create(String url, String tenant, String body) throws Exception {
        String res = mvc.perform(post(url).with(Jwts.admin(tenant)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    @Test
    void full_flow_tenant_company_branch_pos_and_cross_tenant_404() throws Exception {
        String tenantA = createTenant();
        String tenantB = createTenant();

        String company = create("/api/v1/companies", tenantA, """
                {"nit":1003579028,"razonSocial":"Carlos Loza","environment":"PILOT"}""");
        String branch = create("/api/v1/companies/" + company + "/branches", tenantA, """
                {"codigoSucursal":0,"nombre":"Casa Matriz","direccion":"AV. JORGE LOPEZ #123","municipio":"La Paz"}""");
        create("/api/v1/branches/" + branch + "/points-of-sale", tenantA, """
                {"codigoPuntoVenta":1,"nombre":"Caja 1"}""");

        mvc.perform(get("/api/v1/companies/" + company).with(Jwts.admin(tenantA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modality").value("ELECTRONIC_ONLINE"))
                .andExpect(header().exists("X-Correlation-Id"));

        mvc.perform(get("/api/v1/companies/" + company).with(Jwts.admin(tenantB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORG_002"))
                .andExpect(jsonPath("$.category").value("BUSINESS"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void invalid_bodies_are_422_and_missing_idempotency_key_is_422() throws Exception {
        String tenant = createTenant();
        mvc.perform(post("/api/v1/companies").with(Jwts.admin(tenant)).header("Idempotency-Key", "valid-key-0001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nit\":0,\"razonSocial\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.category").value("VALIDATION"));
        mvc.perform(post("/api/v1/companies").with(Jwts.admin(tenant)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":1234567,\"razonSocial\":\"X\",\"environment\":\"PILOT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_001"));
    }

    @Test
    void duplicate_company_with_a_new_key_returns_409() throws Exception {
        String tenant = createTenant();
        String body = "{\"nit\":1234567,\"razonSocial\":\"X\",\"environment\":\"PILOT\"}";
        create("/api/v1/companies", tenant, body);
        mvc.perform(post("/api/v1/companies").with(Jwts.admin(tenant)).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORG_020"));
    }
}
