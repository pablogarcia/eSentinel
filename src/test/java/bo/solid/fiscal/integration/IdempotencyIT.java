package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyIT extends PostgresIT {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tenant() throws Exception {
        String res = mvc.perform(post("/api/v1/tenants").with(Jwts.platform()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"I " + UUID.randomUUID() + "\"}")).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    private MockHttpServletResponse postCompany(String tenant, String key, long nit) throws Exception {
        return mvc.perform(post("/api/v1/companies").with(Jwts.admin(tenant)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nit\":" + nit + ",\"razonSocial\":\"X\",\"environment\":\"PILOT\"}"))
                .andReturn().getResponse();
    }

    @Test
    void retry_with_same_key_and_payload_replays_the_original_response_without_a_second_company() throws Exception {
        String t = tenant();
        MockHttpServletResponse first = postCompany(t, "retry-key-0001", 700001L);
        MockHttpServletResponse second = postCompany(t, "retry-key-0001", 700001L);

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(second.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(first.getHeader("Idempotent-Replayed")).isEqualTo("false");
        assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
        mvc.perform(get("/api/v1/companies").with(Jwts.admin(t))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void same_key_with_a_different_payload_is_409() throws Exception {
        String t = tenant();
        postCompany(t, "reuse-key-0001", 700002L);
        mvc.perform(post("/api/v1/companies").with(Jwts.admin(t)).header("Idempotency-Key", "reuse-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":700003,\"razonSocial\":\"X\",\"environment\":\"PILOT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_002"));
    }

    @Test
    void the_same_key_in_two_tenants_is_independent() throws Exception {
        String a = tenant(), b = tenant();
        assertThat(postCompany(a, "shared-key-0001", 700004L).getStatus()).isEqualTo(201);
        MockHttpServletResponse inB = postCompany(b, "shared-key-0001", 700004L);
        assertThat(inB.getStatus()).isEqualTo(201);
        assertThat(inB.getHeader("Idempotent-Replayed")).isEqualTo("false");
    }

    @Test
    void a_failed_request_does_not_consume_the_key() throws Exception {
        String t = tenant();
        // Primero falla por validacion de dominio (modalidad no soportada) => 422, la clave sigue libre.
        mvc.perform(post("/api/v1/companies").with(Jwts.admin(t)).header("Idempotency-Key", "fail-key-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nit\":700005,\"razonSocial\":\"X\",\"environment\":\"PILOT\",\"modality\":\"COMPUTERIZED_ONLINE\"}"))
                .andExpect(status().isUnprocessableEntity());
        // Mismo key con payload corregido: ya no hay registro previo, procede.
        assertThat(postCompany(t, "fail-key-00001", 700005L).getStatus()).isEqualTo(201);
    }

    @Test
    void thirty_simultaneous_retries_create_exactly_one_company() throws Exception {
        String t = tenant();
        int n = 30;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<MockHttpServletResponse> call = () -> {
                start.await();
                return postCompany(t, "burst-key-00001", 700006L);
            };
            futures.add(pool.submit(call));
        }
        start.countDown();
        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (Future<MockHttpServletResponse> f : futures) responses.add(f.get());
        pool.shutdown();

        assertThat(responses).allMatch(r -> r.getStatus() == 201);
        Set<String> bodies = responses.stream().map(r -> {
            try { return r.getContentAsString(); } catch (Exception e) { throw new RuntimeException(e); }
        }).collect(Collectors.toSet());
        assertThat(bodies).hasSize(1);
        assertThat(responses.stream().filter(r -> "false".equals(r.getHeader("Idempotent-Replayed"))).count())
                .isEqualTo(1);
        mvc.perform(get("/api/v1/companies").with(Jwts.admin(t))).andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
