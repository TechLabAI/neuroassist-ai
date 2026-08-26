package com.neuroassist.pac.application.port.out;

import java.util.UUID;

/**
 * Outbound port — asks hist-service to destroy a patient's encryption key (LGPD crypto-shredding).
 *
 * <p>This is the half of the erasure that pac-service cannot do itself: the audit payloads live in
 * hist-service, encrypted with a per-patient key. Destroying the key makes every payload about that
 * patient unreadable while leaving the records and the hash chain intact (ADR-006) — the chain
 * still verifies, it just verifies over ciphertext nobody can open.
 *
 * <p><b>Fail-closed.</b> Unlike audit-fact writing, this must not be absorbed: reporting a
 * successful erasure while the personal data survives elsewhere is precisely the failure the
 * regulation is about.
 */
public interface HistErasurePort {

    /**
     * @throws HistErasureFailedException when hist-service cannot be reached or refuses
     */
    void erasePatientData(UUID pacienteId, String tenantId);

    /** Raised when the hist-service half of the erasure did not happen. */
    class HistErasureFailedException extends RuntimeException {
        public HistErasureFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
