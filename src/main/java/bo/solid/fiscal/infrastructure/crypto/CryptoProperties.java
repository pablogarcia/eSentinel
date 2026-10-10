package bo.solid.fiscal.infrastructure.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Llavero del cifrado local AES-256-GCM. Cada clave es 32 bytes en Base64.
 * Variables de entorno: SOLID_FISCAL_CRYPTO_ACTIVE_KEY_ID y SOLID_FISCAL_CRYPTO_KEYS_<ID>.
 * En produccion se sustituye por KMS (ADR-0008): este llavero local es para desarrollo y pruebas.
 */
@ConfigurationProperties("solid.fiscal.crypto")
public record CryptoProperties(String activeKeyId, Map<String, String> keys) {
    public CryptoProperties {
        keys = keys == null ? Map.of() : Map.copyOf(keys);
    }
}
