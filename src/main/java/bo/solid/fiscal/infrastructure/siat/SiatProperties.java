package bo.solid.fiscal.infrastructure.siat;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del adaptador SOAP del SIAT. authHeaderName/Prefix: verificado contra el piloto el 2026-10-10:
 * el header "apikey" es el reconocido; "Authorization: Token" se ignora (ADR-0011).
 */
@ConfigurationProperties("solid.fiscal.siat")
public record SiatProperties(Map<String, String> endpoints, Integer connectTimeoutMs, Integer receiveTimeoutMs,
                             String authHeaderName, String authHeaderPrefix) {

    public SiatProperties {
        endpoints = endpoints == null ? Map.of() : Map.copyOf(endpoints);
        connectTimeoutMs = connectTimeoutMs == null ? 5_000 : connectTimeoutMs;
        receiveTimeoutMs = receiveTimeoutMs == null ? 15_000 : receiveTimeoutMs;
        authHeaderName = authHeaderName == null ? "apikey" : authHeaderName;
        authHeaderPrefix = authHeaderPrefix == null ? "TokenApi " : authHeaderPrefix;
    }
}
