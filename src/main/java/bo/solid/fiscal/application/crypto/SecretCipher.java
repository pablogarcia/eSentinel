package bo.solid.fiscal.application.crypto;

/** Cifrado de secretos (token delegado, .p12). Adaptadores: AES-GCM local (dev/test) y KMS (produccion, ADR-0008). */
public interface SecretCipher {
    /** @param aad datos asociados autenticados: ligan el cifrado a su fila (un ciphertext copiado a otra fila no descifra). */
    String encrypt(String plaintext, String aad);

    String decrypt(String stored, String aad);

    /** Identificador de la clave con la que se cifro (para rotacion). */
    String keyIdOf(String stored);
}
