package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.application.port.in.PairingUseCase;
import com.neuroassist.edge.shared.audit.AuditAction;
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
@RequestMapping("/edge-service/pairing")
@RequiredArgsConstructor
public class PairingController {

    private final PairingUseCase pairingUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('edge.pairing.criar')")
    @AuditAction("CREATE_PAIRING")
    public ResponseEntity<Void> pair() {
        // TODO: delegate to PairingUseCase
        throw new UnsupportedOperationException("pair not implemented");
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('edge.pairing.excluir')")
    @AuditAction("DELETE_PAIRING")
    public ResponseEntity<Void> unpair() {
        // TODO: delegate to PairingUseCase
        throw new UnsupportedOperationException("unpair not implemented");
    }
}
