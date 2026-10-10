package bo.solid.fiscal.interfaces.rest;

import bo.solid.fiscal.application.idempotency.IdempotencyService;
import bo.solid.fiscal.application.idempotency.StoredResponse;
import bo.solid.fiscal.domain.shared.FiscalException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Aplica Idempotency-Key a un POST: valida la clave, calcula el hash de la solicitud y delega en IdempotencyService. */
@Component
class IdempotencySupport {

    static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._:\\-]{8,128}$");

    record Created(int status, String location, Object body) {}

    private final IdempotencyService service;
    private final ObjectMapper json;

    IdempotencySupport(IdempotencyService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    ResponseEntity<String> run(String key, String method, String path, Object request,
                               java.util.function.Supplier<Created> operation) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw FiscalException.unprocessable("IDEMPOTENCY_001",
                    "Falta o es invalido el header Idempotency-Key (8..128 caracteres [A-Za-z0-9._:-])");
        }
        String hash = sha256(method + "\n" + path + "\n" + write(request));
        IdempotencyService.Result result = service.execute(key, hash, () -> {
            Created c = operation.get();
            return new StoredResponse(c.status(), c.location(), write(c.body()));
        });
        StoredResponse r = result.response();
        ResponseEntity.BodyBuilder b = ResponseEntity.status(HttpStatusCode.valueOf(r.status()))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()));
        if (r.location() != null) b.header(HttpHeaders.LOCATION, r.location());
        return b.body(r.body());
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar", e);
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
