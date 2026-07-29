package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
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
@RequestMapping("/edge-service/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatUseCase heartbeatUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('edge.heartbeat.criar')")
    @AuditAction("CREATE_HEARTBEAT")
    public ResponseEntity<Void> recordHeartbeat() {
        // TODO: delegate to HeartbeatUseCase
        throw new UnsupportedOperationException("recordHeartbeat not implemented");
    }
}
