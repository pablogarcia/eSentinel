package bo.solid.fiscal.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.infrastructure.crypto.AesGcmSecretCipher;
import bo.solid.fiscal.infrastructure.crypto.CryptoProperties;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    private static String key(String seed32) { return Base64.getEncoder().encodeToString(seed32.getBytes()); }

    private static final String K1 = key("k1-0123456789abcdef0123456789abc");
    private static final String K2 = key("k2-0123456789abcdef0123456789abc");

    private static AesGcmSecretCipher cipher(String active, Map<String, String> keys) {
        return new AesGcmSecretCipher(new CryptoProperties(active, keys));
    }

    @Test
    void round_trip_and_ciphertext_does_not_contain_the_plaintext() {
        var c = cipher("k1", Map.of("k1", K1));
        String stored = c.encrypt("TOKEN-SUPER-SECRETO", "ctx");
        assertThat(stored).startsWith("v1:k1:").doesNotContain("TOKEN-SUPER-SECRETO");
        assertThat(c.decrypt(stored, "ctx")).isEqualTo("TOKEN-SUPER-SECRETO");
        assertThat(c.keyIdOf(stored)).isEqualTo("k1");
    }

    @Test
    void every_encryption_uses_a_fresh_iv() {
        var c = cipher("k1", Map.of("k1", K1));
        assertThat(c.encrypt("x", "ctx")).isNotEqualTo(c.encrypt("x", "ctx"));
    }

    @Test
    void ciphertext_is_bound_to_its_context_aad() {
        var c = cipher("k1", Map.of("k1", K1));
        String stored = c.encrypt("secret", "fiscal-token:tenantA:companyA");
        assertThatThrownBy(() -> c.decrypt(stored, "fiscal-token:tenantA:companyB"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tampering_is_detected() {
        var c = cipher("k1", Map.of("k1", K1));
        String stored = c.encrypt("secret", "ctx");
        String[] p = stored.split(":");
        byte[] ct = Base64.getDecoder().decode(p[3]);
        ct[0] ^= 1;
        String tampered = String.join(":", p[0], p[1], p[2], Base64.getEncoder().encodeToString(ct));
        assertThatThrownBy(() -> c.decrypt(tampered, "ctx")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void key_rotation_keeps_old_secrets_readable_while_new_ones_use_the_new_key() {
        String old = cipher("k1", Map.of("k1", K1)).encrypt("old-secret", "ctx");
        var rotated = cipher("k2", Map.of("k1", K1, "k2", K2));
        assertThat(rotated.decrypt(old, "ctx")).isEqualTo("old-secret");
        assertThat(rotated.keyIdOf(rotated.encrypt("new", "ctx"))).isEqualTo("k2");
        // Si se retira la clave vieja del llavero, ese secreto ya no se puede leer (hay que re-cifrar antes).
        assertThatThrownBy(() -> cipher("k2", Map.of("k2", K2)).decrypt(old, "ctx"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("k1");
    }

    @Test
    void fails_fast_on_missing_or_invalid_configuration() {
        assertThatThrownBy(() -> cipher("", Map.of("k1", K1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher(null, Map.of())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("k9", Map.of("k1", K1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("k1", Map.of("k1", key("too-short")))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> cipher("k1", Map.of("k1", "%%%no-base64%%%"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("bad:id", Map.of("bad:id", K1))).isInstanceOf(IllegalStateException.class);
    }
}
