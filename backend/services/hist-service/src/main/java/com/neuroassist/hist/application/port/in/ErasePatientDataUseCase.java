package com.neuroassist.hist.application.port.in;

import java.util.UUID;

/** Inbound use-case — LGPD data erasure (crypto-shredding) for one patient (P10, HIST-024). */
public interface ErasePatientDataUseCase {

    record EraseCommand(UUID subjectId, String tenantId, String requestedBy) {}

    /**
     * Erases the per-patient AES encryption key. All existing audit records for this patient will
     * have their {@code payload} field become unreadable. The hash chain structure (recordHash,
     * prevHash, sequenceNo) is never touched and remains verifiable.
     */
    void erase(EraseCommand command);
}
