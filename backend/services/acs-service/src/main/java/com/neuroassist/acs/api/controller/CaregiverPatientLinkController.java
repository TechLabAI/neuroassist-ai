package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.CaregiverPatientLinkUseCase;
import com.neuroassist.acs.shared.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/acs-service/caregiver-patient-link")
@RequiredArgsConstructor
public class CaregiverPatientLinkController {

    private final CaregiverPatientLinkUseCase caregiverPatientLinkUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('acs.caregiver-patient-link.vincular')")
    public ResponseEntity<Void> link() {
        // TODO: delegate to CaregiverPatientLinkUseCase
        throw new UnsupportedOperationException("link not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('acs.caregiver-patient-link.read')")
    public ResponseEntity<Void> listLinks() {
        // TODO: delegate to CaregiverPatientLinkUseCase
        throw new UnsupportedOperationException("listLinks not implemented");
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('acs.caregiver-patient-link.cancelar')")
    @AuditAction("DELETE_CAREGIVER_PATIENT_LINK")
    public ResponseEntity<Void> revokeLink() {
        // TODO: delegate to CaregiverPatientLinkUseCase
        throw new UnsupportedOperationException("revokeLink not implemented");
    }
}
