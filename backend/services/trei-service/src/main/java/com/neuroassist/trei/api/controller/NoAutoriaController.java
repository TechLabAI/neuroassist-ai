package com.neuroassist.trei.api.controller;

import com.neuroassist.trei.application.port.in.NoAutoriaUseCase;
import com.neuroassist.trei.shared.audit.AuditAction;
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
@RequestMapping("/trei-service/no-autoria")
@RequiredArgsConstructor
public class NoAutoriaController {

    private final NoAutoriaUseCase noAutoriaUseCase;

    @PutMapping("/materialize-node")
    @PreAuthorize("hasAuthority('trei.no-autoria.criar')")
    @AuditAction("CREATE_NO_AUTORIA")
    public ResponseEntity<Void> materializeNode() {
        // TODO[SCAFFOLD]: delegate to NoAutoriaUseCase
        throw new UnsupportedOperationException("materializeNode not implemented");
    }

    @PutMapping("/approve-caregiver")
    @PreAuthorize("hasAuthority('trei.no-autoria.aprovar')")
    @AuditAction("UPDATE_NO_AUTORIA")
    public ResponseEntity<Void> approveCaregiver() {
        // TODO[SCAFFOLD]: delegate to NoAutoriaUseCase
        throw new UnsupportedOperationException("approveCaregiver not implemented");
    }

    @PutMapping("/approve-patient")
    @PreAuthorize("hasAuthority('trei.no-autoria.aprovar')")
    @AuditAction("UPDATE_NO_AUTORIA")
    public ResponseEntity<Void> approvePatient() {
        // TODO[SCAFFOLD]: delegate to NoAutoriaUseCase
        throw new UnsupportedOperationException("approvePatient not implemented");
    }

    @PutMapping("/inspect-node")
    @PreAuthorize("hasAuthority('trei.no-autoria.read')")
    public ResponseEntity<Void> inspectNode() {
        // TODO[SCAFFOLD]: delegate to NoAutoriaUseCase
        throw new UnsupportedOperationException("inspectNode not implemented");
    }

    @PutMapping("/discard-node")
    @PreAuthorize("hasAuthority('trei.no-autoria.descartar')")
    @AuditAction("UPDATE_NO_AUTORIA")
    public ResponseEntity<Void> discardNode() {
        // TODO[SCAFFOLD]: delegate to NoAutoriaUseCase
        throw new UnsupportedOperationException("discardNode not implemented");
    }
}
