package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
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
@RequestMapping("/evnt-service/notification-orchestration")
@RequiredArgsConstructor
public class NotificationOrchestrationController {

    private final NotificationOrchestrationUseCase notificationOrchestrationUseCase;

    @PutMapping("/{id}/orchestrate")
    @PreAuthorize("hasAuthority('evnt.notification-orchestration.processar')")
    @AuditAction("CREATE_NOTIFICATION_ORCHESTRATION")
    public ResponseEntity<Void> orchestrate() {
        // TODO: delegate to NotificationOrchestrationUseCase
        throw new UnsupportedOperationException("orchestrate not implemented");
    }

    @PutMapping("/{id}/retrySweep")
    @PreAuthorize("hasAuthority('evnt.notification-orchestration.reprocessar')")
    public ResponseEntity<Void> retrySweep() {
        // TODO: delegate to NotificationOrchestrationUseCase
        throw new UnsupportedOperationException("retrySweep not implemented");
    }
}
