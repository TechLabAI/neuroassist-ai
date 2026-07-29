package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.EventConfirmationUseCase;
import com.neuroassist.evnt.shared.audit.AuditAction;
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
@RequestMapping("/evnt-service/event-confirmation")
@RequiredArgsConstructor
public class EventConfirmationController {

    private final EventConfirmationUseCase eventConfirmationUseCase;

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('evnt.event-confirmation.confirmar')")
    @AuditAction("UPDATE_EVENT_CONFIRMATION")
    public ResponseEntity<Void> confirm() {
        // TODO: delegate to EventConfirmationUseCase
        throw new UnsupportedOperationException("confirm not implemented");
    }

    @PutMapping("/{id}/acknowledgeDispatch")
    @PreAuthorize("hasAuthority('evnt.event-confirmation.confirmar')")
    @AuditAction("UPDATE_EVENT_CONFIRMATION")
    public ResponseEntity<Void> acknowledgeDispatch() {
        // TODO: delegate to EventConfirmationUseCase
        throw new UnsupportedOperationException("acknowledgeDispatch not implemented");
    }
}
