package com.neuroassist.hist.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-patient AES-256 key descriptor used for LGPD crypto-shredding (P10, HIST-024).
 *
 * <p>Erasure: {@code keyCiphertext} and {@code keyIv} are set to {@code null} and {@code
 * erasedAt} is stamped. Once erased, the patient's payload can no longer be decrypted —
 * the hash chain structure remains verifiable. Zero Spring/JPA (RULE-01, RULE-02).
 */
public record PatientEncryptionKey(
        UUID subjectId,
        String tenantId,
        /** AES-256 patient key wrapped (encrypted) with the service master key. Null when erased. */
        byte[] keyCiphertext,
        /** IV used to wrap the patient key with the master key. Null when erased. */
        byte[] keyIv,
        Instant createdAt,
        /** Non-null when erased (LGPD audit trail). */
        Instant erasedAt) {

    public boolean isErased() {
        return erasedAt != null;
    }
}
