package bo.solid.fiscal.interfaces.security;

import bo.solid.fiscal.domain.shared.ErrorCategory;
import bo.solid.fiscal.interfaces.rest.ApiError;
import bo.solid.fiscal.interfaces.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Escribe el modelo de error unico tambien para errores que ocurren en filtros (401/403). */
@Component
public class ApiErrorWriter {

    private final ObjectMapper json;

    ApiErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), new ApiError(code, ErrorCategory.SECURITY.name(), message,
                List.of(), null, MDC.get(CorrelationIdFilter.MDC_KEY), Instant.now()));
    }
}
