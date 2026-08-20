package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.ErasePatientDataUseCase;
import com.neuroassist.hist.application.port.out.PatientKeyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — LGPD crypto-shredding erasure (P10, HIST-024).
 *
 * <p>Erases the per-patient AES-256 key so that existing payload ciphertext can never be
 * decrypted again. The audit record rows and the hash chain structure (recordHash, prevHash,
 * sequenceNo) are untouched — the chain remains structurally verifiable (ADR-004 / ADR-006).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErasePatientDataService implements ErasePatientDataUseCase {

    private final PatientKeyPort patientKeyPort;

    @Override
    @Transactional
    public void erase(EraseCommand command) {
        log.info(
                "LGPD erasure initiated: subjectId={} tenant={} requestedBy={}",
                command.subjectId(), command.tenantId(), command.requestedBy());
        patientKeyPort.erase(command.subjectId(), command.tenantId());
        log.info(
                "LGPD erasure complete: subjectId={} tenant={}",
                command.subjectId(), command.tenantId());
    }
}
