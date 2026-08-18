package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.ConfirmationProxyUseCase;
import com.neuroassist.dash.shared.audit.AuditAction;
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
@RequestMapping("/dash-service/confirmation-proxy")
@RequiredArgsConstructor
public class ConfirmationProxyController {

    private final ConfirmationProxyUseCase confirmationProxyUseCase;

    @PutMapping("/{id}/confirmEvent")
    @PreAuthorize("hasAuthority('dash.confirmation-proxy.confirmar')")
    @AuditAction("UPDATE_CONFIRMATION_PROXY")
    public ResponseEntity<Void> confirmEvent() {
        // TODO: delegate to ConfirmationProxyUseCase
        throw new UnsupportedOperationException("confirmEvent not implemented");
    }
}
