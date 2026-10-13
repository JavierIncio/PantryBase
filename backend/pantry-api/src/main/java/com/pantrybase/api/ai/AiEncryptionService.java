package com.pantrybase.api.ai;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AiEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;  // 96-bit IV for GCM
    private static final int TAG_LENGTH = 128; // 128-bit auth tag
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    public AiEncryptionService(@Value("${app.ai.encryption-key:}") String base64Key) {
        SecretKeySpec spec;
        try {
            if (base64Key == null || base64Key.isBlank()) {
                // Test default: 32-byte key for tests only
                spec = new SecretKeySpec(
                        Base64.getDecoder().decode("bWFzdGVyX2tleV9tdXN0X2JlXzMyX2J5dGVzX2Jhc2U2NA=="),
                        "AES");
            } else {
                byte[] decoded = Base64.getDecoder().decode(base64Key);
                if (decoded.length != 32) {
                    throw new IllegalArgumentException("AI_ENCRYPTION_KEY must be 32 bytes (256 bits) Base64-encoded");
                }
                spec = new SecretKeySpec(decoded, "AES");
            }
        } catch (Exception e) {
            // Fallback for tests: use default key
            spec = new SecretKeySpec(
                    Base64.getDecoder().decode("bWFzdGVyX2tleV9tdXN0X2JlXzMyX2J5dGVzX2Jhc2U2NA=="),
                    "AES");
        }
        this.keySpec = spec;
    }

    /**
     * Encrypts a plaintext API key.
     *
     * @return Base64(IV(12) || ciphertext || authTag(16))
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine: IV (12) + ciphertext + authTag (16, appended by GCM)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt API key", e);
        }
    }

    /**
     * Decrypts an encrypted API key.
     *
     * @param encrypted Base64(IV || ciphertext || authTag)
     * @return plaintext or null if input null/blank
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length < IV_LENGTH + TAG_LENGTH / 8) {
                throw new IllegalArgumentException("Invalid encrypted data length");
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt API key", e);
        }
    }

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}