package com.neuroassist.dash.api.controller;

import com.neuroassist.dash.application.port.in.PanelStreamUseCase;
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
@RequestMapping("/dash-service/panel-stream")
@RequiredArgsConstructor
public class PanelStreamController {

    private final PanelStreamUseCase panelStreamUseCase;

    @PostMapping("/open-session")
    @PreAuthorize("hasAuthority('dash.panel-stream.abrir')")
    @AuditAction("CREATE_PANEL_STREAM")
    public ResponseEntity<Void> openSession() {
        // TODO: delegate to PanelStreamUseCase
        throw new UnsupportedOperationException("openSession not implemented");
    }

    @PostMapping("/heartbeat")
    @PreAuthorize("hasAuthority('dash.panel-stream.criar')")
    @AuditAction("CREATE_PANEL_STREAM")
    public ResponseEntity<Void> heartbeat() {
        // TODO: delegate to PanelStreamUseCase
        throw new UnsupportedOperationException("heartbeat not implemented");
    }

    @PostMapping("/close-session")
    @PreAuthorize("hasAuthority('dash.panel-stream.fechar')")
    @AuditAction("UPDATE_PANEL_STREAM")
    public ResponseEntity<Void> closeSession() {
        // TODO: delegate to PanelStreamUseCase
        throw new UnsupportedOperationException("closeSession not implemented");
    }

    @PutMapping("/{id}/replayCatchup")
    @PreAuthorize("hasAuthority('dash.panel-stream.reprocessar')")
    public ResponseEntity<Void> replayCatchup() {
        // TODO: delegate to PanelStreamUseCase
        throw new UnsupportedOperationException("replayCatchup not implemented");
    }
}
