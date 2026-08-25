package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.CaregiverPatientLinkResponse;
import com.neuroassist.acs.application.port.in.CaregiverPatientLinkUseCase;
import com.neuroassist.acs.shared.audit.AuditAction;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller — CaregiverPatientLinkController. Endpoints for caregiver-patient link
 * management. SAFETY-CRITICAL (D-204): revoke endpoint enforces coverage invariant via service
 * layer. RULE-04: no infrastructure dependency.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acs/caregivers")
@RequiredArgsConstructor
public class CaregiverPatientLinkController {

    private final CaregiverPatientLinkUseCase caregiverPatientLinkUseCase;

    /**
     * POST /api/v1/acs/caregivers/{cuidadorId}/patients/{pacienteId}/link Creates an active link
     * between a caregiver and a patient. Requires: acs.link.create
     */
    @PostMapping("/{cuidadorId}/patients/{pacienteId}/link")
    @PreAuthorize("hasAuthority('acs.vinculo.criar')")
    @AuditAction("CREATE_CAREGIVER_PATIENT_LINK")
    public ResponseEntity<CaregiverPatientLinkResponse> createLink(
            @PathVariable UUID cuidadorId,
            @PathVariable UUID pacienteId,
            @RequestParam UUID tenantId) {
        // Return 200 for idempotent request (link already exists), 201 for new link
        Optional<CaregiverPatientLinkResponse> existing =
                caregiverPatientLinkUseCase.findActiveLink(cuidadorId, pacienteId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }
        CaregiverPatientLinkResponse response =
                caregiverPatientLinkUseCase.createLink(cuidadorId, pacienteId, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * DELETE /api/v1/acs/caregivers/{cuidadorId}/patients/{pacienteId}/link Revokes the active link
     * between a caregiver and a patient. SAFETY-CRITICAL (D-204): returns 409 if this is the last
     * active link for the patient. Requires: acs.link.revoke
     */
    @DeleteMapping("/{cuidadorId}/patients/{pacienteId}/link")
    @PreAuthorize("hasAuthority('acs.vinculo.revogar')")
    @AuditAction("REVOKE_CAREGIVER_PATIENT_LINK")
    public ResponseEntity<CaregiverPatientLinkResponse> revokeLink(
            @PathVariable UUID cuidadorId, @PathVariable UUID pacienteId) {
        CaregiverPatientLinkResponse response =
                caregiverPatientLinkUseCase.revokeLink(cuidadorId, pacienteId);
        return ResponseEntity.ok(response);
    }
}
