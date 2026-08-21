package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.api.dto.ErasePatientDataResponse;
import com.neuroassist.hist.application.port.in.ErasePatientDataUseCase;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller — LGPD patient data erasure (P10, HIST-024).
 *
 * <p>Erases the per-patient encryption key so that all existing payload ciphertext becomes
 * unreadable. The hash chain structure is preserved and remains verifiable (ADR-004 / ADR-006).
 *
 * <p>Requires scope {@code hist:erase} — typically held only by LGPD compliance operators.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hist/patients")
@RequiredArgsConstructor
public class PatientDataErasureController {

    private final ErasePatientDataUseCase erasePatientDataUseCase;

    @DeleteMapping("/{subjectId}/personal-data")
    @PreAuthorize("hasAuthority('SCOPE_hist:erase')")
    public ResponseEntity<ErasePatientDataResponse> erasePersonalData(
            @PathVariable UUID subjectId,
            @RequestHeader("X-Tenant-Id") String tenantId) {

        erasePatientDataUseCase.erase(
                new ErasePatientDataUseCase.EraseCommand(subjectId, tenantId, "api-request"));

        return ResponseEntity.ok(new ErasePatientDataResponse(
                subjectId,
                tenantId,
                Instant.now(),
                "Patient payload data erased. Hash chain structure remains intact (ADR-006)."));
    }
}
