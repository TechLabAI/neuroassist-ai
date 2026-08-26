package com.neuroassist.hist.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption/decryption for payload crypto-shredding (P10, HIST-024).
 *
 * <p>Two-layer encryption:
 *
 * <ol>
 *   <li>Service master key wraps each per-patient AES-256 key (stored in {@code
 *       patient_encryption_key}).
 *   <li>Per-patient key encrypts the payload JSON. Ciphertext stored as {@code
 *       "base64(iv):base64(ciphertext)"} in the {@code audit_record.payload} TEXT column.
 * </ol>
 *
 * <p>No external library — JDK {@code javax.crypto} only.
 */
@Slf4j
@Component
public class AesGcmPayloadCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12; // 96-bit IV — recommended for AES-GCM
    private static final int KEY_BYTES = 32; // 256-bit key

    private final SecretKey masterKey;
    private final SecureRandom rng = new SecureRandom();

    public AesGcmPayloadCrypto(@Value("${hist.crypto.master-key}") String masterKeyHex) {
        byte[] raw = hexToBytes(masterKeyHex);
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException(
                    "hist.crypto.master-key must be 64 hex chars (32 bytes / 256 bits)");
        }
        this.masterKey = new SecretKeySpec(raw, "AES");
    }

    // ---- Per-patient key lifecycle ----

    public byte[] generatePatientKey() {
        byte[] key = new byte[KEY_BYTES];
        rng.nextBytes(key);
        return key;
    }

    /** Encrypts a raw patient key with the master key. Returns [ciphertext, iv]. */
    public byte[][] wrapPatientKey(byte[] rawKey) {
        byte[] iv = randomIv();
        return new byte[][] {aesGcmEncrypt(rawKey, masterKey, iv), iv};
    }

    /** Decrypts a wrapped patient key using the master key. */
    public byte[] unwrapPatientKey(byte[] ciphertext, byte[] iv) {
        return aesGcmDecrypt(ciphertext, masterKey, iv);
    }

    // ---- Payload encryption ----

    /**
     * Encrypts a JSON payload string with the patient key.
     *
     * @return {@code "base64(iv):base64(ciphertext)"} stored in the {@code payload} column
     */
    public String encryptPayload(String plaintext, byte[] patientKeyBytes) {
        byte[] iv = randomIv();
        SecretKey key = new SecretKeySpec(patientKeyBytes, "AES");
        byte[] ct = aesGcmEncrypt(plaintext.getBytes(StandardCharsets.UTF_8), key, iv);
        return Base64.getEncoder().encodeToString(iv)
                + ":"
                + Base64.getEncoder().encodeToString(ct);
    }

    /**
     * Decrypts a payload column value in {@code "base64(iv):base64(ciphertext)"} format.
     *
     * @return plaintext JSON string
     */
    public String decryptPayload(String encoded, byte[] patientKeyBytes) {
        int sep = encoded.indexOf(':');
        if (sep < 0) {
            throw new IllegalArgumentException("Encrypted payload missing IV separator");
        }
        byte[] iv = Base64.getDecoder().decode(encoded.substring(0, sep));
        byte[] ct = Base64.getDecoder().decode(encoded.substring(sep + 1));
        SecretKey key = new SecretKeySpec(patientKeyBytes, "AES");
        return new String(aesGcmDecrypt(ct, key, iv), StandardCharsets.UTF_8);
    }

    // ---- AES-GCM primitives ----

    private byte[] aesGcmEncrypt(byte[] plaintext, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    private byte[] aesGcmDecrypt(byte[] ciphertext, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_BYTES];
        rng.nextBytes(iv);
        return iv;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] =
                    (byte)
                            ((Character.digit(hex.charAt(i), 16) << 4)
                                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
