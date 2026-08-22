package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.PatientEncryptionKey;
import java.util.Optional;
import java.util.UUID;

/** Outbound port — per-patient AES-256 key lifecycle for LGPD crypto-shredding (P10, HIST-024). */
public interface PatientKeyPort {
    /** Returns the key entry (active or already erased) for the given patient+tenant, if any. */
    Optional<PatientEncryptionKey> find(UUID subjectId, String tenantId);

    /** Persists a newly generated key entry. */
    PatientEncryptionKey create(PatientEncryptionKey key);

    /** Erases key material: nulls ciphertext+iv, stamps erased_at. Idempotent. */
    void erase(UUID subjectId, String tenantId);
}
