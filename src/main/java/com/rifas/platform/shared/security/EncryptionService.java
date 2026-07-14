package com.rifas.platform.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES     = 12;

    private final SecretKeySpec secretKey;

    public EncryptionService(@Value("${app.encryption.key}") String key) {
        byte[] keyBytes = Base64.getDecoder().decode(key.trim());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "app.encryption.key debe ser exactamente 32 bytes en Base64 (44 caracteres). " +
                "Generar con: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] data       = Base64.getDecoder().decode(encrypted);
            byte[] iv         = Arrays.copyOfRange(data, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_BYTES, data.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
