package bo.solid.fiscal.infrastructure.crypto;

import bo.solid.fiscal.application.crypto.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM con IV aleatorio de 96 bits por cifrado y AAD obligatoria. Formato: v1:keyId:iv:ciphertext+tag (Base64).
 * Falla al arrancar si no hay clave activa valida (fail-fast): jamas se guarda un secreto sin cifrar por omision.
 */
@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final Pattern KEY_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKey> keys = new HashMap<>();
    private final String activeKeyId;

    public AesGcmSecretCipher(CryptoProperties props) {
        if (props.activeKeyId() == null || !KEY_ID.matcher(props.activeKeyId()).matches()) {
            throw new IllegalStateException("solid.fiscal.crypto.active-key-id es obligatorio ([A-Za-z0-9_-], max 64)");
        }
        props.keys().forEach((id, b64) -> {
            if (!KEY_ID.matcher(id).matches()) throw new IllegalStateException("Identificador de clave invalido: " + id);
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(b64.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("La clave '" + id + "' no es Base64 valido");
            }
            if (raw.length != 32) {
                throw new IllegalStateException("La clave '" + id + "' debe tener 32 bytes (AES-256), tiene " + raw.length);
            }
            keys.put(id, new SecretKeySpec(raw, "AES"));
        });
        if (!keys.containsKey(props.activeKeyId())) {
            throw new IllegalStateException("No existe la clave activa '" + props.activeKeyId() + "' en solid.fiscal.crypto.keys");
        }
        this.activeKeyId = props.activeKeyId();
    }

    @Override
    public String encrypt(String plaintext, String aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyId), new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b64 = Base64.getEncoder();
            return "v1:" + activeKeyId + ":" + b64.encodeToString(iv) + ":" + b64.encodeToString(ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto", e);
        }
    }

    @Override
    public String decrypt(String stored, String aad) {
        String[] p = stored.split(":", -1);
        if (p.length != 4 || !"v1".equals(p[0])) {
            throw new IllegalStateException("Formato de secreto cifrado desconocido");
        }
        SecretKey key = keys.get(p[1]);
        if (key == null) {
            throw new IllegalStateException("La clave de cifrado '" + p[1] + "' ya no esta en el llavero");
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(p[2])));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(Base64.getDecoder().decode(p[3])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Sin detalle: no revelar si fallo la clave, la AAD o la integridad.
            throw new IllegalStateException("No se pudo descifrar el secreto (clave, contexto o integridad invalidos)");
        }
    }

    @Override
    public String keyIdOf(String stored) {
        String[] p = stored.split(":", -1);
        if (p.length != 4) throw new IllegalStateException("Formato de secreto cifrado desconocido");
        return p[1];
    }
}
